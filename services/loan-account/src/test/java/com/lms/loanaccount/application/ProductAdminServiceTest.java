package com.lms.loanaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.loanaccount.adapters.out.product.DbProductCatalog;
import com.lms.loanaccount.application.port.out.ProductRepository;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import com.lms.security.audit.AuditEntry;
import com.lms.security.audit.AuditRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductAdminServiceTest {

    private static final String PRODUCT_ID = "MICRO-V1";
    private static final java.util.Currency INR = java.util.Currency.getInstance("INR");

    private InMemoryProducts products;
    private CapturingAudit audit;
    private ProductAdminService service;

    @BeforeEach
    void setUp() {
        products = new InMemoryProducts();
        // Seed v1 effective from the start of the year.
        products.insert(product(1, "24", 50_000_00), LocalDate.parse("2026-01-01"));
        audit = new CapturingAudit();
        service =
                new ProductAdminService(
                        products,
                        () -> LocalDate.parse("2026-06-01"),
                        audit,
                        Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC),
                        PRODUCT_ID);
    }

    @Test
    void createVersionAssignsNextVersionAndKeepsExistingLoansOnTheirVersion() {
        ProductVersion v2 =
                service.createVersion(
                        new NewProductVersionRequest(
                                "INR",
                                1_000_00,
                                60_000_00,
                                50_000,
                                List.of(3, 6, 9, 12),
                                "26",
                                "2",
                                "18",
                                "36",
                                3,
                                "0",
                                LocalDate.parse("2026-06-01")),
                        "admin-b",
                        "admin");

        assertThat(v2.product().version()).isEqualTo(2);
        assertThat(v2.product().annualInterestRate()).isEqualTo(Rate.percent("26"));

        // current() now resolves v2 (effective 2026-06-01, business date 2026-06-01).
        DbProductCatalog catalog =
                new DbProductCatalog(products, () -> LocalDate.parse("2026-06-01"), PRODUCT_ID);
        assertThat(catalog.current().version()).isEqualTo(2);

        // An existing loan pinned to v1 still reads v1's 24% APR — untouched by the new version.
        Product pinned = catalog.byVersion(PRODUCT_ID, 1);
        assertThat(pinned.version()).isEqualTo(1);
        assertThat(pinned.annualInterestRate()).isEqualTo(Rate.percent("24"));
    }

    @Test
    void createVersionIsAuditedWithPiiFreeBeforeAndAfterSummaries() {
        service.createVersion(
                new NewProductVersionRequest(
                        "INR",
                        1_000_00,
                        60_000_00,
                        50_000,
                        List.of(3, 6, 9, 12),
                        "26",
                        "2",
                        "18",
                        "36",
                        3,
                        "0",
                        LocalDate.parse("2026-06-01")),
                "admin-b",
                "admin");

        AuditEntry entry = audit.last;
        assertThat(entry).isNotNull();
        assertThat(entry.actorId()).isEqualTo("admin-b");
        assertThat(entry.action()).isEqualTo("product.version.created");
        assertThat(entry.targetType()).isEqualTo("product");
        assertThat(entry.targetId()).isEqualTo("MICRO-V1 v2");
        assertThat(entry.beforeSummary()).contains("apr=24");
        assertThat(entry.afterSummary()).contains("version=2", "apr=26");
    }

    @Test
    void currentReflectsEffectiveDating() {
        // A version effective in the future is not yet current.
        service.createVersion(
                new NewProductVersionRequest(
                        "INR",
                        1_000_00,
                        60_000_00,
                        50_000,
                        List.of(3, 6, 9, 12),
                        "30",
                        "2",
                        "18",
                        "36",
                        3,
                        "0",
                        LocalDate.parse("2027-01-01")),
                "admin-b",
                "admin");
        // Business date 2026-06-01 → still v1.
        assertThat(service.current().product().version()).isEqualTo(1);
        assertThat(service.listVersions()).hasSize(2);
    }

    private static Product product(int version, String apr, long maxPrincipal) {
        return new FixedTenorReducingBalanceProduct(
                PRODUCT_ID,
                version,
                INR,
                Money.of(5_000_00, INR),
                Money.of(maxPrincipal, INR),
                Money.of(50_000, INR),
                List.of(3, 6, 9, 12),
                Rate.percent(apr),
                Rate.percent("2"),
                Rate.percent("18"),
                Rate.percent("36"),
                3,
                Rate.percent("0"));
    }

    private static final class InMemoryProducts implements ProductRepository {
        private final List<ProductVersion> rows = new ArrayList<>();

        @Override
        public List<ProductVersion> listVersions(String productId) {
            return rows.stream()
                    .filter(v -> v.product().productId().equals(productId))
                    .sorted(
                            Comparator.comparingInt((ProductVersion v) -> v.product().version())
                                    .reversed())
                    .toList();
        }

        @Override
        public Optional<Product> findVersion(String productId, int version) {
            return rows.stream()
                    .map(ProductVersion::product)
                    .filter(p -> p.productId().equals(productId) && p.version() == version)
                    .findFirst();
        }

        @Override
        public Optional<ProductVersion> latestAsOf(String productId, LocalDate asOf) {
            return rows.stream()
                    .filter(v -> v.product().productId().equals(productId))
                    .filter(v -> !v.effectiveFrom().isAfter(asOf))
                    .max(Comparator.comparingInt(v -> v.product().version()));
        }

        @Override
        public int nextVersion(String productId) {
            return rows.stream()
                            .filter(v -> v.product().productId().equals(productId))
                            .mapToInt(v -> v.product().version())
                            .max()
                            .orElse(0)
                    + 1;
        }

        @Override
        public void insert(Product product, LocalDate effectiveFrom) {
            rows.add(new ProductVersion(product, effectiveFrom, Instant.EPOCH));
        }
    }

    private static final class CapturingAudit implements AuditRecorder {
        private AuditEntry last;

        @Override
        public void record(AuditEntry entry) {
            this.last = entry;
        }
    }
}
