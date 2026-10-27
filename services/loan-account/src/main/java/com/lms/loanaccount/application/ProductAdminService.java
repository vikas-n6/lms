package com.lms.loanaccount.application;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.port.out.ProductRepository;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import com.lms.security.audit.AuditEntry;
import com.lms.security.audit.AuditOutcome;
import com.lms.security.audit.AuditRecorder;
import com.lms.security.audit.AuditSummary;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Admin product configuration (phase prompt task 2): view the current version, list all versions
 * and create a new effective-dated version. Creating a version appends an immutable row with the
 * next version number; existing loans keep their pinned version (they resolve {@code
 * ProductCatalog.byVersion}), so a new version never changes an outstanding loan's terms. Each
 * creation is audited (ADR 0014) with PII-free before/after summaries.
 */
public class ProductAdminService {

    private final ProductRepository products;
    private final BusinessCalendar calendar;
    private final AuditRecorder audit;
    private final Clock clock;
    private final String productId;

    public ProductAdminService(
            ProductRepository products,
            BusinessCalendar calendar,
            AuditRecorder audit,
            Clock clock,
            String productId) {
        this.products = Objects.requireNonNull(products);
        this.calendar = Objects.requireNonNull(calendar);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.productId = Objects.requireNonNull(productId);
    }

    public ProductVersion current() {
        return products.latestAsOf(productId, calendar.today())
                .orElseThrow(() -> new IllegalStateException("no product version in effect"));
    }

    public List<ProductVersion> listVersions() {
        return products.listVersions(productId);
    }

    public ProductVersion createVersion(
            NewProductVersionRequest request, String actorId, String actorRoles) {
        Optional<ProductVersion> previous = products.latestAsOf(productId, calendar.today());
        int next = products.nextVersion(productId);
        Currency currency = Currency.getInstance(request.currency());
        Product product =
                new FixedTenorReducingBalanceProduct(
                        productId,
                        next,
                        currency,
                        Money.of(request.minPrincipalMinor(), currency),
                        Money.of(request.maxPrincipalMinor(), currency),
                        Money.of(request.principalStepMinor(), currency),
                        request.tenors(),
                        Rate.percent(request.aprPercent()),
                        Rate.percent(request.feePercent()),
                        Rate.percent(request.gstPercent()),
                        Rate.percent(request.penalAprPercent()),
                        request.graceDays(),
                        Rate.percent(request.foreclosureChargePercent()));

        products.insert(product, request.effectiveFrom());
        audit.record(
                AuditEntry.of(
                        actorId,
                        actorRoles,
                        "product.version.created",
                        "product",
                        productId + " v" + next,
                        previous.map(p -> summary(p.product()).render()).orElse(null),
                        summary(product).render(),
                        AuditOutcome.SUCCESS));
        return new ProductVersion(product, request.effectiveFrom(), clock.instant());
    }

    private static AuditSummary summary(Product p) {
        return AuditSummary.create()
                .with("version", p.version())
                .with("apr", p.annualInterestRate().asPlainString())
                .with("fee", p.processingFeeRate().asPlainString())
                .with("gst", p.gstRate().asPlainString())
                .with("penalApr", p.penalAnnualRate().asPlainString())
                .with("graceDays", p.graceDays())
                .with("principalMin", p.minPrincipal().minorUnits())
                .with("principalMax", p.maxPrincipal().minorUnits())
                .with("tenors", p.tenors());
    }
}
