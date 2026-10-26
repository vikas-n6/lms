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
        Map<String, Money> amounts,
        String externalReference,
        Long externalAmountMinor) {

    public PostingRequest {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amounts, "amounts");
        amounts = Map.copyOf(amounts);
    }

    /** The common case: an internal event with no external (bank/rail) reference to reconcile. */
    public PostingRequest(
            String eventType,
            UUID sourceEventId,
            String loanId,
            String customerId,
            Currency currency,
            Map<String, Money> amounts) {
        this(eventType, sourceEventId, loanId, customerId, currency, amounts, null, null);
    }

    /** A money-movement event carrying a bank/rail reference and the amount that moved. */
    public PostingRequest withExternal(String reference, Money amount) {
        return new PostingRequest(
                eventType,
                sourceEventId,
                loanId,
                customerId,
                currency,
                amounts,
                reference,
                amount == null ? null : amount.minorUnits());
    }
}
