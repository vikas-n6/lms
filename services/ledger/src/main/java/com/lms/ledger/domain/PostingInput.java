package com.lms.ledger.domain;

import com.lms.money.Money;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * The amounts and dimensions a {@link PostingTemplate} needs, extracted from a source event by the
 * inbound adapter so the domain stays free of Avro/transport types (docs/architecture.md hexagonal
 * rule 1). Amounts are looked up by name (e.g. {@code net}, {@code principal}, {@code fee}, {@code
 * gst}, {@code interest}).
 */
public record PostingInput(
        String loanId, String customerId, Currency currency, Map<String, Money> amounts) {

    public PostingInput {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amounts, "amounts");
        amounts = Map.copyOf(amounts);
    }

    /** The named amount, or an error if the event did not supply it. */
    public Money amount(String name) {
        Money money = amounts.get(name);
        if (money == null) {
            throw new IllegalArgumentException(
                    "missing amount '" + name + "' for posting template");
        }
        if (!money.currency().equals(currency)) {
            throw new IllegalArgumentException("amount '" + name + "' is in the wrong currency");
        }
        return money;
    }
}
