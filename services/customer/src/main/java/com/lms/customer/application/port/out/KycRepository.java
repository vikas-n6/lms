package com.lms.customer.application.port.out;

import com.lms.customer.domain.KycStatus;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistence for KYC records. The id number is stored only as ciphertext plus a keyed blind index
 * for lookup — never in plaintext (CLAUDE.md).
 */
public interface KycRepository {

    void save(
            String customerId,
            KycStatus status,
            String encryptedIdNumber,
            String idNumberBlindIndex,
            Instant updatedAt);

    /** Update only the status (e.g. when an async callback resolves a PENDING record). */
    void updateStatus(String customerId, KycStatus status, Instant updatedAt);

    Optional<KycStatus> statusOf(String customerId);
}
