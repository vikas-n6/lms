package com.lms.loanaccount.adapters.out.product;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ProductRepository;
import com.lms.schedule.Product;
import java.util.Objects;

/**
 * Reads the active product from the versioned {@code products} table (phase prompt task 2). {@code
 * current()} is the version in effect as of the business date; {@code byVersion} returns the exact
 * pinned version a loan was created under, so adding a new version never changes an existing loan's
 * terms (docs/domain-rules.md "The one product"). Product parameters are configuration in the
 * database, never literals in business code.
 */
public class DbProductCatalog implements ProductCatalog {

    private final ProductRepository products;
    private final BusinessCalendar calendar;
    private final String productId;

    public DbProductCatalog(
            ProductRepository products, BusinessCalendar calendar, String productId) {
        this.products = Objects.requireNonNull(products);
        this.calendar = Objects.requireNonNull(calendar);
        this.productId = Objects.requireNonNull(productId);
    }

    @Override
    public Product current() {
        return products.latestAsOf(productId, calendar.today())
                .orElseThrow(() -> new IllegalStateException("no product version in effect"))
                .product();
    }

    @Override
    public Product byVersion(String productId, int version) {
        return products.findVersion(productId, version)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "unknown product version " + productId + " v" + version));
    }
}
