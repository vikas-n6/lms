package com.lms.loanaccount.application.port.out;

import com.lms.schedule.Product;

/**
 * Supplies the lending product (docs/domain-rules.md "The one product"). loan-account owns the
 * versioned {@code products} table; a loan pins the version it was created under and reads that
 * version for the life of the loan, never a later one.
 */
public interface ProductCatalog {

    /** The current (latest) product version. */
    Product current();

    /**
     * The product as it was at {@code version}; for the single v1 product this is {@link #current}.
     */
    Product byVersion(String productId, int version);
}
