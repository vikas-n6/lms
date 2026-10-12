package com.lms.customer.application;

import com.lms.customer.application.port.out.CustomerEventPublisher;
import com.lms.customer.application.port.out.KycClient;
import com.lms.customer.application.port.out.KycRepository;
import com.lms.customer.domain.KycStatus;
import com.lms.security.crypto.BlindIndex;
import com.lms.security.crypto.FieldCipher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KYC submission (phase prompt S3): encrypt the id number (AES-GCM) with a keyed blind index for
 * lookup, call the KYC simulator behind Resilience4j, persist the record, and publish {@code
 * customer.kyc.completed} on a terminal result. A PENDING result is resolved later by {@link
 * #onCallback}. The id number is never stored or logged in plaintext.
 */
@Service
public class KycService {

    private final KycClient kycClient;
    private final KycRepository repository;
    private final CustomerEventPublisher events;
    private final FieldCipher idCipher;
    private final BlindIndex idBlindIndex;
    private final Clock clock;
    private final String callbackUrl;

    public KycService(
            KycClient kycClient,
            KycRepository repository,
            CustomerEventPublisher events,
            FieldCipher idNumberCipher,
            BlindIndex idNumberBlindIndex,
            Clock clock,
            @Value("${lms.customer.kyc-callback-url:http://localhost:8101/customers/kyc/callback}")
                    String callbackUrl) {
        this.kycClient = kycClient;
        this.repository = repository;
        this.events = events;
        this.idCipher = idNumberCipher;
        this.idBlindIndex = idNumberBlindIndex;
        this.clock = clock;
        this.callbackUrl = callbackUrl;
    }

    @Transactional
    public KycStatus submit(String customerId, String idNumber) {
        String encrypted = idCipher.encrypt(idNumber);
        String blindIndex = idBlindIndex.compute(idNumber);
        KycStatus status = kycClient.verify(customerId, callbackUrl);
        repository.save(customerId, status, encrypted, blindIndex, clock.instant());
        if (status != KycStatus.PENDING) {
            events.kycCompleted(customerId, status, clock.instant());
        }
        return status;
    }

    /** Resolve a PENDING record from the simulator's asynchronous callback. */
    @Transactional
    public void onCallback(String customerId, KycStatus finalStatus) {
        repository.updateStatus(customerId, finalStatus, clock.instant());
        events.kycCompleted(customerId, finalStatus, clock.instant());
    }
}
