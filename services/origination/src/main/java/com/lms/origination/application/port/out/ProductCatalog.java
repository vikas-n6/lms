package com.lms.origination.application.port.out;

import com.lms.schedule.Product;

/**
 * Supplies the active product used to price offers (versioned configuration, not Java literals).
 */
public interface ProductCatalog {
    Product current();
}
