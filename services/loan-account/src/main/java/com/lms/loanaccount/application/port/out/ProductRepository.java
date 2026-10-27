package com.lms.loanaccount.application.port.out;

import com.lms.loanaccount.application.ProductVersion;
import com.lms.schedule.Product;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the versioned {@code products} table (docs/domain-rules.md "The one product"). A
 * new version is a new row with the next version number and an effective-from date; existing loans
 * keep the version they were created under (resolved via {@link #findVersion}).
 */
public interface ProductRepository {

    /** All versions of a product, newest version first. */
    List<ProductVersion> listVersions(String productId);

    /** A specific pinned version (what an existing loan reads). */
    Optional<Product> findVersion(String productId, int version);

    /**
     * The product in effect as of {@code asOf}: highest version with {@code effective_from <=
     * asOf}.
     */
    Optional<ProductVersion> latestAsOf(String productId, LocalDate asOf);

    /** The next version number to assign for a new version of this product. */
    int nextVersion(String productId);

    /** Insert a new, immutable product version effective from the given date. */
    void insert(Product product, LocalDate effectiveFrom);
}
