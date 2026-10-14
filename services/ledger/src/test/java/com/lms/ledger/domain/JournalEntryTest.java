package com.lms.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Currency INR = Currency.getInstance("INR");

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    private static JournalEntry entry(List<Posting> postings) {
        return new JournalEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "disbursement.succeeded",
                INR,
                postings,
                "loan-1",
                "cust-1",
                null,
                Instant.parse("2026-10-14T00:00:00Z"));
    }

    @Test
    void acceptsABalancedEntry() {
        JournalEntry e =
                entry(
                        List.of(
                                Posting.debit(
                                        AccountCode.LOAN_PRINCIPAL_RECEIVABLE,
                                        inr(5_000_000),
                                        "loan-1",
                                        "cust-1"),
                                Posting.credit(
                                        AccountCode.DISBURSEMENT_IN_TRANSIT,
                                        inr(4_882_000),
                                        "loan-1",
                                        "cust-1"),
                                Posting.credit(
                                        AccountCode.PROCESSING_FEE_INCOME,
                                        inr(100_000),
                                        "loan-1",
                                        "cust-1"),
                                Posting.credit(
                                        AccountCode.GST_PAYABLE, inr(18_000), "loan-1", "cust-1")));

        assertThat(e.totalDebits()).isEqualTo(inr(5_000_000));
        assertThat(e.totalCredits()).isEqualTo(inr(5_000_000));
    }

    @Test
    void rejectsAnUnbalancedEntry() {
        assertThatThrownBy(
                        () ->
                                entry(
                                        List.of(
                                                Posting.debit(
                                                        AccountCode.LOAN_PRINCIPAL_RECEIVABLE,
                                                        inr(5_000_000),
                                                        "l",
                                                        "c"),
                                                Posting.credit(
                                                        AccountCode.DISBURSEMENT_IN_TRANSIT,
                                                        inr(4_000_000),
                                                        "l",
                                                        "c"))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void reversingFlipsEverySideAndLinksBack() {
        JournalEntry original =
                entry(
                        List.of(
                                Posting.debit(
                                        AccountCode.INTEREST_RECEIVABLE,
                                        inr(3_288),
                                        "loan-1",
                                        "cust-1"),
                                Posting.credit(
                                        AccountCode.INTEREST_INCOME,
                                        inr(3_288),
                                        "loan-1",
                                        "cust-1")));

        JournalEntry reversal =
                original.reverse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-10-15T00:00:00Z"));

        assertThat(reversal.reversesEntryId()).isEqualTo(original.id());
        assertThat(reversal.totalDebits()).isEqualTo(original.totalCredits());
        assertThat(reversal.postings().get(0).side()).isEqualTo(PostingSide.CREDIT);
        assertThat(reversal.postings().get(1).side()).isEqualTo(PostingSide.DEBIT);
    }
}
