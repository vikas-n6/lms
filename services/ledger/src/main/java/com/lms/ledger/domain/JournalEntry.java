package com.lms.ledger.domain;

import com.lms.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, balanced double-entry journal entry (docs/domain-rules.md "Ledger invariants"). The
 * entry rejects itself in the constructor if debits do not equal credits per currency, mirroring
 * the Postgres deferred constraint trigger — the domain is a second line of defence. A correction
 * is a new entry with swapped postings and {@link #reversesEntryId} set; nothing is ever updated.
 *
 * @param id UUIDv7 entry id
 * @param sourceEventId the event that caused the entry; unique, makes posting idempotent
 * @param sourceEventType e.g. {@code disbursement.succeeded}
 * @param currency the single currency of the entry
 * @param postings the debit/credit lines (at least two, balanced)
 * @param loanId entry-level loan dimension (nullable)
 * @param customerId entry-level customer dimension (nullable)
 * @param reversesEntryId the entry this one reverses, or null
 * @param occurredAt when the entry was posted (UTC)
 */
public record JournalEntry(
        UUID id,
        UUID sourceEventId,
        String sourceEventType,
        Currency currency,
        List<Posting> postings,
        String loanId,
        String customerId,
        UUID reversesEntryId,
        Instant occurredAt) {

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(sourceEventType, "sourceEventType");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(postings, "postings");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (postings.size() < 2) {
            throw new IllegalArgumentException("a journal entry needs at least two postings");
        }
        postings = List.copyOf(postings);
        long debits = 0L;
        long credits = 0L;
        for (Posting posting : postings) {
            if (!posting.amount().currency().equals(currency)) {
                throw new IllegalArgumentException(
                        "posting currency does not match the entry currency");
            }
            if (posting.side() == PostingSide.DEBIT) {
                debits += posting.amount().minorUnits();
            } else {
                credits += posting.amount().minorUnits();
            }
        }
        if (debits != credits) {
            throw new UnbalancedEntryException(debits, credits, currency);
        }
    }

    public Money totalDebits() {
        return sum(PostingSide.DEBIT);
    }

    public Money totalCredits() {
        return sum(PostingSide.CREDIT);
    }

    private Money sum(PostingSide side) {
        Money total = Money.zero(currency);
        for (Posting posting : postings) {
            if (posting.side() == side) {
                total = total.add(posting.amount());
            }
        }
        return total;
    }

    /**
     * A mirrored entry that reverses this one: every posting flips side, {@code reversesEntryId}
     * points here, and the source event is the reversal request.
     */
    public JournalEntry reverse(UUID newId, UUID reversalEventId, Instant at) {
        List<Posting> mirrored = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            mirrored.add(posting.reversed());
        }
        return new JournalEntry(
                newId,
                reversalEventId,
                sourceEventType + ".reversal",
                currency,
                mirrored,
                loanId,
                customerId,
                id,
                at);
    }
}
