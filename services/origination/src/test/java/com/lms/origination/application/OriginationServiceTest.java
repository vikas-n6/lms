package com.lms.origination.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.origination.adapters.out.compliance.PermissiveCompliancePolicy;
import com.lms.origination.application.port.out.ApplicationEventPublisher;
import com.lms.origination.application.port.out.ApplicationRepository;
import com.lms.origination.application.port.out.CustomerProfileProjection;
import com.lms.origination.application.port.out.EsignOtp;
import com.lms.origination.application.port.out.ProductCatalog;
import com.lms.origination.domain.Application;
import com.lms.origination.domain.ApplicationId;
import com.lms.origination.domain.ApplicationStatus;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.security.crypto.BlindIndex;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OriginationServiceTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T04:30:00Z"), ZoneOffset.UTC);

    private static Product product() {
        return new FixedTenorReducingBalanceProduct(
                "MICRO-V1",
                1,
                INR,
                Money.of(500_000L, INR),
                Money.of(5_000_000L, INR),
                Money.of(50_000L, INR),
                List.of(3, 6, 9, 12),
                Rate.percent(24),
                Rate.percent(2),
                Rate.percent(18),
                Rate.percent(36),
                3,
                Rate.zero());
    }

    private static final class FakeRepo implements ApplicationRepository {
        final Map<String, Application> store = new HashMap<>();

        @Override
        public void save(Application a) {
            store.put(a.id().toString(), a);
        }

        @Override
        public Optional<Application> findById(ApplicationId id) {
            return Optional.ofNullable(store.get(id.toString()));
        }

        @Override
        public List<Application> list(String s, int limit, int offset) {
            return List.copyOf(store.values());
        }

        @Override
        public List<Application> findExpired(Instant now) {
            return store.values().stream()
                    .filter(a -> a.status() instanceof ApplicationStatus.Offered)
                    .filter(a -> a.offer().isPresent() && a.offer().get().isExpired(now))
                    .toList();
        }
    }

    private static final class FakePublisher implements ApplicationEventPublisher {
        final List<String> events = new ArrayList<>();

        public void submitted(Application a, ApplicantSnapshotData s) {
            events.add("submitted:" + s.ageYears() + ":" + s.bureauSubjectPhone());
        }

        public void offered(Application a) {
            events.add("offered");
        }

        public void accepted(Application a) {
            events.add("accepted");
        }

        public void rejected(Application a, List<String> r) {
            events.add("rejected:" + r);
        }

        public void expired(Application a) {
            events.add("expired");
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final FakePublisher publisher = new FakePublisher();
    private boolean otpValid = true;

    private OriginationService service(CustomerProfileProjection.Snapshot profile) {
        CustomerProfileProjection customers = id -> Optional.of(profile);
        ProductCatalog catalog = OriginationServiceTest::product;
        EsignOtp esign =
                new EsignOtp() {
                    public void issue(String id, String phone) {}

                    public boolean verify(String id, String code) {
                        return otpValid;
                    }
                };
        return new OriginationService(
                repo,
                publisher,
                customers,
                catalog,
                new OfferPricing(new ReducingBalanceScheduleEngine()),
                new PermissiveCompliancePolicy(),
                esign,
                new BlindIndex(new byte[32]),
                clock,
                "Asia/Kolkata",
                48);
    }

    private Application createDraft(OriginationService service) {
        return service.createDraft(
                new CreateDraftCommand(
                        "cust-1",
                        "MICRO-V1",
                        Money.of(5_000_000L, INR),
                        "9000000001",
                        LocalDate.of(1992, 1, 1),
                        Money.of(5_000_000L, INR),
                        "SALARIED",
                        true,
                        true,
                        true));
    }

    @Test
    void submitPublishesTheSnapshotWhenEligible() {
        OriginationService service = service(new CustomerProfileProjection.Snapshot(true, true));
        Application draft = createDraft(service);
        Application submitted = service.submit(draft.id().toString());
        assertThat(submitted.status()).isInstanceOf(ApplicationStatus.Submitted.class);
        assertThat(publisher.events).anyMatch(e -> e.startsWith("submitted:34:9000000001"));
    }

    @Test
    void submitBlockedWhenKycMissing() {
        OriginationService service = service(new CustomerProfileProjection.Snapshot(false, true));
        Application draft = createDraft(service);
        assertThatThrownBy(() -> service.submit(draft.id().toString()))
                .isInstanceOf(OriginationExceptions.SubmissionBlocked.class);
    }

    @Test
    void approveDecisionProducesAnOffer() {
        OriginationService service = service(new CustomerProfileProjection.Snapshot(true, true));
        Application draft = createDraft(service);
        service.submit(draft.id().toString());
        service.onDecision(
                draft.id().toString(),
                OriginationService.DecisionOutcome.APPROVE,
                Money.of(5_000_000L, INR));
        Application offered = repo.findById(draft.id()).orElseThrow();
        assertThat(offered.status()).isInstanceOf(ApplicationStatus.Offered.class);
        assertThat(offered.offer()).isPresent();
        assertThat(publisher.events).contains("offered");
    }

    @Test
    void rejectDecisionRejects() {
        OriginationService service = service(new CustomerProfileProjection.Snapshot(true, true));
        Application draft = createDraft(service);
        service.submit(draft.id().toString());
        service.onDecision(draft.id().toString(), OriginationService.DecisionOutcome.REJECT, null);
        assertThat(repo.findById(draft.id()).orElseThrow().status())
                .isInstanceOf(ApplicationStatus.Rejected.class);
    }

    @Test
    void acceptRequiresValidOtp() {
        OriginationService service = service(new CustomerProfileProjection.Snapshot(true, true));
        Application draft = createDraft(service);
        service.submit(draft.id().toString());
        service.onDecision(
                draft.id().toString(),
                OriginationService.DecisionOutcome.APPROVE,
                Money.of(5_000_000L, INR));

        otpValid = false;
        assertThatThrownBy(() -> service.accept(draft.id().toString(), 12, "000000"))
                .isInstanceOf(OriginationExceptions.AcceptanceRefused.class);

        otpValid = true;
        Application accepted = service.accept(draft.id().toString(), 12, "123456");
        assertThat(accepted.status()).isInstanceOf(ApplicationStatus.Accepted.class);
        assertThat(accepted.acceptedTerms()).isPresent();
        assertThat(publisher.events).contains("accepted");
    }
}
