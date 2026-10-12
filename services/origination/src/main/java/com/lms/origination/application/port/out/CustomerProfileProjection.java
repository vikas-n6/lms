package com.lms.origination.application.port.out;

import java.util.Optional;

/**
 * Read model of customer KYC/bank status, maintained by consuming {@code customer.events} (so
 * origination never calls customer over REST). Used to gate submission.
 */
public interface CustomerProfileProjection {

    Optional<Snapshot> find(String customerId);

    /**
     * @param kycVerified KYC status is VERIFIED
     * @param bankAccountLinked a payout account is linked and penny-drop verified
     */
    record Snapshot(boolean kycVerified, boolean bankAccountLinked) {}
}
