package com.lms.customer.application;

import com.lms.customer.domain.CustomerId;

/** Raised when a customer id does not exist; mapped to a 404 problem detail at the edge. */
public final class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(CustomerId id) {
        super("Customer not found: " + id);
    }
}
