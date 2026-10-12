package com.lms.customer.application.port.out;

import com.lms.customer.domain.ConsentLedger;
import com.lms.customer.domain.ConsentType;
import java.time.Instant;

/** Append-only consent persistence (never updated/deleted; CLAUDE.md). */
public interface ConsentRepository {

    void append(String customerId, ConsentType type, Instant grantedAt);

    ConsentLedger ledgerOf(String customerId);
}
