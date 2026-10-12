package com.lms.origination.application.port.out;

/** Write side of the customer projection, driven by the customer-events listener. */
public interface CustomerProjectionUpdater {

    void markKycVerified(String customerId);

    void markBankLinked(String customerId);
}
