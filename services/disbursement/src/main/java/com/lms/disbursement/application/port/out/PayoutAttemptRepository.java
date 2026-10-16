package com.lms.disbursement.application.port.out;

import com.lms.disbursement.domain.PayoutAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for payout attempts (implemented by a JDBC adapter). */
public interface PayoutAttemptRepository {

    void save(PayoutAttempt attempt);

    Optional<PayoutAttempt> find(UUID disbursementId, int attemptNumber);

    List<PayoutAttempt> findByDisbursementId(UUID disbursementId);
}
