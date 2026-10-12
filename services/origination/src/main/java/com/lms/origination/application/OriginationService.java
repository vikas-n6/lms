package com.lms.origination.application;

import com.lms.money.Money;
import com.lms.origination.application.port.out.ApplicationEventPublisher;
import com.lms.origination.application.port.out.ApplicationRepository;
import com.lms.origination.application.port.out.CompliancePolicy;
import com.lms.origination.application.port.out.CompliancePolicy.ComplianceCheck;
import com.lms.origination.application.port.out.CompliancePolicy.ComplianceResult;
import com.lms.origination.application.port.out.CompliancePolicy.Stage;
import com.lms.origination.application.port.out.CustomerProfileProjection;
import com.lms.origination.application.port.out.EsignOtp;
import com.lms.origination.application.port.out.ProductCatalog;
import com.lms.origination.domain.AcceptedTerms;
import com.lms.origination.domain.Application;
import com.lms.origination.domain.ApplicationId;
import com.lms.origination.domain.Offer;
import com.lms.origination.domain.OfferAcceptance;
import com.lms.schedule.OfferQuote;
import com.lms.security.crypto.BlindIndex;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the application lifecycle (phase prompt S4): create/update draft, submit (guarded,
 * with the self-contained snapshot), react to a decision (offer or reject), OTP e-sign acceptance,
 * and offer expiry. Compliance is checked before offer creation and before acceptance and the
 * result recorded. Each mutation persists the aggregate and writes its outbox event in one
 * transaction.
 */
@Service
public class OriginationService {

    private final ApplicationRepository repository;
    private final ApplicationEventPublisher events;
    private final CustomerProfileProjection customers;
    private final ProductCatalog products;
    private final OfferPricing offerPricing;
    private final CompliancePolicy compliance;
    private final EsignOtp esign;
    private final BlindIndex phoneBlindIndex;
    private final Clock clock;
    private final ZoneId businessZone;
    private final Duration offerTtl;

    public OriginationService(
            ApplicationRepository repository,
            ApplicationEventPublisher events,
            CustomerProfileProjection customers,
            ProductCatalog products,
            OfferPricing offerPricing,
            CompliancePolicy compliance,
            EsignOtp esign,
            BlindIndex phoneBlindIndex,
            Clock clock,
            @Value("${lms.business.zone:Asia/Kolkata}") String businessZone,
            @Value("${lms.origination.offer-ttl-hours:48}") long offerTtlHours) {
        this.repository = repository;
        this.events = events;
        this.customers = customers;
        this.products = products;
        this.offerPricing = offerPricing;
        this.compliance = compliance;
        this.esign = esign;
        this.phoneBlindIndex = phoneBlindIndex;
        this.clock = clock;
        this.businessZone = ZoneId.of(businessZone);
        this.offerTtl = Duration.ofHours(offerTtlHours);
    }

    @Transactional
    public Application createDraft(CreateDraftCommand c) {
        Application app =
                Application.draft(
                        ApplicationId.newId(clock),
                        c.customerId(),
                        c.productId(),
                        c.requestedAmount(),
                        c.phone(),
                        c.dateOfBirth(),
                        c.declaredMonthlyIncome(),
                        c.employmentType(),
                        c.dataProcessingConsent(),
                        c.creditPullConsent(),
                        c.loanAgreementConsent(),
                        clock.instant());
        repository.save(app);
        return app;
    }

    public Application get(String id) {
        return load(id);
    }

    public List<Application> list(String statusCodeOrNull, int limit, int offset) {
        return repository.list(statusCodeOrNull, limit, offset);
    }

    @Transactional
    public Application submit(String id) {
        Application app = load(id);
        CustomerProfileProjection.Snapshot profile =
                customers
                        .find(app.customerId())
                        .orElse(new CustomerProfileProjection.Snapshot(false, false));
        ApplicantProfile applicant =
                new ApplicantProfile(
                        profile.kycVerified(),
                        profile.bankAccountLinked(),
                        app.dataProcessingConsent(),
                        app.creditPullConsent(),
                        app.loanAgreementConsent(),
                        app.dateOfBirth(),
                        app.declaredMonthlyIncome(),
                        app.employmentType(),
                        phoneBlindIndex.compute(app.phone()),
                        app.phone(),
                        Money.zero(app.requestedAmount().currency()),
                        0);
        SubmissionGuards.Result guard = SubmissionGuards.check(applicant);
        if (!guard.allowed()) {
            throw new OriginationExceptions.SubmissionBlocked(guard.missing());
        }
        ApplicantSnapshotData snapshot =
                new ApplicantSnapshotFactory(clock.withZone(businessZone)).snapshot(applicant);
        Application submitted = app.submit(clock.instant());
        repository.save(submitted);
        events.submitted(submitted, snapshot);
        return submitted;
    }

    /** React to a decision.completed event for this application. */
    @Transactional
    public void onDecision(
            String applicationId, DecisionOutcome outcome, Money offeredLimitOrNull) {
        Application app = load(applicationId).recordDecision(clock.instant());
        switch (outcome) {
            case APPROVE -> {
                require(
                        compliance.check(
                                new ComplianceCheck(
                                        Stage.OFFER, app.id().toString(), app.customerId())));
                Optional<List<OfferQuote>> options =
                        offerPricing.price(products.current(), offeredLimitOrNull, businessDate());
                if (options.isEmpty()) {
                    Application rejected = app.reject(clock.instant());
                    repository.save(rejected);
                    events.rejected(rejected, List.of("OFFER_BELOW_MINIMUM"));
                    return;
                }
                Offer offer = Offer.create(options.get(), clock.instant(), offerTtl);
                Application offered = app.withOffer(offer, clock.instant());
                repository.save(offered);
                events.offered(offered);
            }
            case REJECT -> {
                Application rejected = app.reject(clock.instant());
                repository.save(rejected);
                events.rejected(rejected, List.of("DECISION_REJECT"));
            }
            case REFER -> repository.save(app); // awaits a manual-review override (new decision)
        }
    }

    /** Issue the OTP e-sign code for an offered application. */
    public void requestEsign(String id) {
        Application app = load(id);
        esign.issue(app.id().toString(), app.phone());
    }

    @Transactional
    public Application accept(String id, int tenorMonths, String otpCode) {
        Application app = load(id);
        Offer offer =
                app.offer()
                        .orElseThrow(() -> new OriginationExceptions.AcceptanceRefused("no offer"));
        boolean otpValid = esign.verify(app.id().toString(), otpCode);
        OfferAcceptance outcome = offer.accept(tenorMonths, otpValid, clock.instant());
        if (outcome instanceof OfferAcceptance.Refused refused) {
            throw new OriginationExceptions.AcceptanceRefused(refused.reason().name());
        }
        require(
                compliance.check(
                        new ComplianceCheck(
                                Stage.ACCEPTANCE, app.id().toString(), app.customerId())));
        OfferQuote option = ((OfferAcceptance.Accepted) outcome).option();
        AcceptedTerms terms = AcceptedTerms.from(option, "esign:" + app.id());
        Application accepted = app.accept(terms, clock.instant());
        repository.save(accepted);
        events.accepted(accepted);
        return accepted;
    }

    @Transactional
    public Application withdraw(String id) {
        Application app = load(id).withdraw(clock.instant());
        repository.save(app);
        return app;
    }

    /** Expire offered applications past their offer expiry (scheduled). */
    @Transactional
    public int expireOffers() {
        List<Application> expired = repository.findExpired(clock.instant());
        for (Application app : expired) {
            Application e = app.expire(clock.instant());
            repository.save(e);
            events.expired(e);
        }
        return expired.size();
    }

    /** The outcome carried by decision.completed, decoupled from the decision service's enum. */
    public enum DecisionOutcome {
        APPROVE,
        REJECT,
        REFER
    }

    private Application load(String id) {
        return repository
                .findById(ApplicationId.of(id))
                .orElseThrow(() -> new OriginationExceptions.NotFound(id));
    }

    private LocalDate businessDate() {
        return LocalDate.ofInstant(clock.instant(), businessZone);
    }

    private static void require(ComplianceResult result) {
        if (!result.allowed()) {
            throw new OriginationExceptions.ComplianceDeclined(result.detail());
        }
    }
}
