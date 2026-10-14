package com.lms.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

/**
 * Stateful ledger-invariant property test (phase prompt "Ledger invariant property test"): apply
 * random sequences of accrual days and reversals to an in-memory book and, after every step, assert
 * the trial balance nets to zero, no must-be-non-negative account goes negative, and the per-loan
 * receivables equal the independently tracked loan balances (docs/domain-rules.md "Ledger
 * invariants"). Runs on the pure domain, so it is provable without a database; the SQL-constraint,
 * replay and concurrency variants are integration tests deferred to the Docker session.
 */
class LedgerInvariantPropertyTest {

    private static final long PRINCIPAL = 5_000_000L;
    private static final long NET = 4_882_000L;
    private static final long FEE = 100_000L;
    private static final long GST = 18_000L;

    private final PostingTemplateRegistry registry = PostingTemplateRegistry.defaults();

    @Provide
    Arbitrary<Currency> currencies() {
        return Arbitraries.of(
                Currency.getInstance("INR"),
                Currency.getInstance("JPY"),
                Currency.getInstance("KWD"));
    }

    @Property(tries = 2_000)
    void invariantsHoldAfterEveryStep(
            @ForAll @Size(min = 1, max = 30)
                    List<@IntRange(min = 1, max = 100_000) Integer> accruals,
            @ForAll long reverseSeed,
            @ForAll("currencies") Currency ccy) {
        List<JournalEntry> book = new ArrayList<>();
        book.add(
                build(
                        "disbursement.succeeded",
                        ccy,
                        Map.of(
                                "principal", Money.of(PRINCIPAL, ccy),
                                "net", Money.of(NET, ccy),
                                "fee", Money.of(FEE, ccy),
                                "gst", Money.of(GST, ccy))));

        long expectedPrincipalReceivable = PRINCIPAL;
        long expectedInterestReceivable = 0L;
        assertInvariants(book, expectedPrincipalReceivable, expectedInterestReceivable);

        Random random = new Random(reverseSeed);
        Deque<Long> reversibleInterest = new ArrayDeque<>();

        for (int amount : accruals) {
            book.add(
                    build("loan.interest-accrued", ccy, Map.of("interest", Money.of(amount, ccy))));
            expectedInterestReceivable += amount;
            reversibleInterest.push((long) amount);
            assertInvariants(book, expectedPrincipalReceivable, expectedInterestReceivable);

            if (random.nextBoolean() && !reversibleInterest.isEmpty()) {
                // Reverse the most recent accrual: find its entry and mirror it.
                JournalEntry toReverse = lastAccrual(book);
                book.add(
                        toReverse.reverse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.parse("2026-10-14T00:00:00Z")));
                expectedInterestReceivable -= reversibleInterest.pop();
                assertInvariants(book, expectedPrincipalReceivable, expectedInterestReceivable);
            }
        }
    }

    @Test
    void reversingRestoresBalancesAndTheBookStaysBalanced() {
        Currency inr = Currency.getInstance("INR");
        List<JournalEntry> book = new ArrayList<>();
        JournalEntry accrual =
                build("loan.interest-accrued", inr, Map.of("interest", Money.of(3_288, inr)));
        book.add(accrual);
        long before = TrialBalance.of(book).normalBalanceOf(AccountCode.INTEREST_RECEIVABLE);
        assertThat(before).isEqualTo(3_288L);

        book.add(accrual.reverse(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH));
        TrialBalance tb = TrialBalance.of(book);
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.normalBalanceOf(AccountCode.INTEREST_RECEIVABLE)).isZero();
        assertThat(tb.normalBalanceOf(AccountCode.INTEREST_INCOME)).isZero();
    }

    private JournalEntry lastAccrual(List<JournalEntry> book) {
        for (int i = book.size() - 1; i >= 0; i--) {
            JournalEntry e = book.get(i);
            if (e.sourceEventType().equals("loan.interest-accrued")) {
                return e;
            }
        }
        throw new IllegalStateException("no accrual to reverse");
    }

    private void assertInvariants(
            List<JournalEntry> book,
            long expectedPrincipalReceivable,
            long expectedInterestReceivable) {
        TrialBalance tb = TrialBalance.of(book);
        assertThat(tb.balanced()).as("trial balance nets to zero").isTrue();
        assertThat(tb.normalBalanceOf(AccountCode.LOAN_PRINCIPAL_RECEIVABLE))
                .isEqualTo(expectedPrincipalReceivable);
        assertThat(tb.normalBalanceOf(AccountCode.INTEREST_RECEIVABLE))
                .isEqualTo(expectedInterestReceivable);
        // Receivables must never go negative.
        assertThat(tb.normalBalanceOf(AccountCode.LOAN_PRINCIPAL_RECEIVABLE))
                .isGreaterThanOrEqualTo(0L);
        assertThat(tb.normalBalanceOf(AccountCode.INTEREST_RECEIVABLE)).isGreaterThanOrEqualTo(0L);
    }

    private JournalEntry build(String eventType, Currency ccy, Map<String, Money> amounts) {
        List<Posting> postings =
                registry.find(eventType)
                        .orElseThrow()
                        .build(new PostingInput("loan-1", "cust-1", ccy, amounts));
        return new JournalEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                ccy,
                postings,
                "loan-1",
                "cust-1",
                null,
                Instant.parse("2026-10-14T00:00:00Z"));
    }
}
