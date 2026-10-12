package com.lms.customer.application;

import com.lms.customer.application.port.out.ConsentRepository;
import com.lms.customer.domain.ConsentLedger;
import com.lms.customer.domain.ConsentType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records consent grants (append-only) and reads the ledger (phase prompt S3). */
@Service
public class ConsentService {

    private final ConsentRepository repository;
    private final Clock clock;

    public ConsentService(ConsentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void grant(String customerId, ConsentType type) {
        repository.append(customerId, type, clock.instant());
    }

    public ConsentLedger ledger(String customerId) {
        return repository.ledgerOf(customerId);
    }
}
