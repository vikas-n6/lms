package com.lms.ledger.application;

import com.lms.money.Money;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to post a ledger entry for a source event (built by the inbound adapter from a loan or
 * disbursement event). The template is chosen by {@link #eventType}; idempotency is on {@link
 * #sourceEventId} (docs/domain-rules.md "Ledger invariants" rule 3).
 */
public record PostingRequest(
        String eventType,
        UUID sourceEventId,
        String loanId,
        String customerId,
        Currency currency,
        Map<String, Money> amounts) {

    public PostingRequest {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amounts, "amounts");
        amounts = Map.copyOf(amounts);
    }
}
