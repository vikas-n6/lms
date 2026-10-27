package com.lms.loanaccount.adapters.in.web;

import com.lms.loanaccount.application.NewProductVersionRequest;
import com.lms.loanaccount.application.ProductAdminService;
import com.lms.loanaccount.application.ProductVersion;
import com.lms.schedule.Product;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product-configuration admin endpoints (phase prompt task 2): view the current version, list all
 * versions and create a new effective-dated version. Existing loans keep their pinned version, so
 * creating a version never changes an outstanding loan. Routed behind the gateway at {@code
 * /api/admin/products}. The acting admin is the JWT subject (request context) with an {@code
 * X-Acting-User} fallback for local/dev; creation is audited (ADR 0014).
 */
@RestController
@RequestMapping("/admin/products")
public class ProductController {

    private final ProductAdminService service;

    public ProductController(ProductAdminService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/current")
    public ProductView current() {
        return ProductView.of(service.current());
    }

    @GetMapping
    public List<ProductView> list() {
        return service.listVersions().stream().map(ProductView::of).toList();
    }

    @PostMapping
    public ProductView create(
            @RequestBody NewProductVersionRequest request,
            @RequestHeader(value = "X-Acting-User", required = false) String actingUserHeader,
            @RequestHeader(value = "X-Acting-Roles", required = false) String actingRoles) {
        String actor = actor(actingUserHeader);
        return ProductView.of(
                service.createVersion(request, actor, actingRoles == null ? "" : actingRoles));
    }

    private static String actor(String headerFallback) {
        String fromContext =
                com.lms.security.context.RequestContextHolder.currentIfBound()
                        .map(com.lms.security.context.RequestContext::userId)
                        .orElse(null);
        String actor = fromContext != null ? fromContext : headerFallback;
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("the acting admin user is required");
        }
        return actor;
    }

    /** UI-friendly view of a product version (plain strings/minor units, currency code). */
    public record ProductView(
            String productId,
            int version,
            String currency,
            long minPrincipalMinor,
            long maxPrincipalMinor,
            long principalStepMinor,
            List<Integer> tenors,
            String aprPercent,
            String feePercent,
            String gstPercent,
            String penalAprPercent,
            int graceDays,
            String foreclosureChargePercent,
            LocalDate effectiveFrom,
            Instant createdAt) {

        static ProductView of(ProductVersion v) {
            Product p = v.product();
            return new ProductView(
                    p.productId(),
                    p.version(),
                    p.currency().getCurrencyCode(),
                    p.minPrincipal().minorUnits(),
                    p.maxPrincipal().minorUnits(),
                    p.principalStep().minorUnits(),
                    p.tenors(),
                    p.annualInterestRate().asPlainString(),
                    p.processingFeeRate().asPlainString(),
                    p.gstRate().asPlainString(),
                    p.penalAnnualRate().asPlainString(),
                    p.graceDays(),
                    p.foreclosureChargeRate().asPlainString(),
                    v.effectiveFrom(),
                    v.createdAt());
        }
    }
}
