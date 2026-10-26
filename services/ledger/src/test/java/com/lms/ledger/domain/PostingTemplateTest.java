package com.lms.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Posting templates produce the balanced postings from docs/domain-rules.md "Posting templates".
 */
class PostingTemplateTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final PostingTemplateRegistry registry = PostingTemplateRegistry.defaults();

    private static Money inr(long m) {
        return Money.of(m, INR);
    }

    private long debitsFor(String eventType, Map<String, Money> amounts) {
        var template = registry.find(eventType).orElseThrow();
        var postings = template.build(new PostingInput("loan-1", "cust-1", INR, amounts));
        return postings.stream()
                .filter(p -> p.side() == PostingSide.DEBIT)
                .mapToLong(p -> p.amount().minorUnits())
                .sum();
    }

    @Test
    void disbursementSucceededBalancesAndDebitsPrincipalReceivable() {
        var template = registry.find("disbursement.succeeded").orElseThrow();
        var postings =
                template.build(
                        new PostingInput(
                                "loan-1",
                                "cust-1",
                                INR,
                                Map.of(
                                        "principal", inr(5_000_000),
                                        "net", inr(4_882_000),
                                        "fee", inr(100_000),
                                        "gst", inr(18_000))));

        long debits =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.DEBIT)
                        .mapToLong(p -> p.amount().minorUnits())
                        .sum();
        long credits =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.CREDIT)
                        .mapToLong(p -> p.amount().minorUnits())
                        .sum();
        assertThat(debits).isEqualTo(credits).isEqualTo(5_000_000L);
        // The single debit is principal receivable and equals the loan principal.
        Posting debit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.DEBIT)
                        .findFirst()
                        .orElseThrow();
        assertThat(debit.account()).isEqualTo(AccountCode.LOAN_PRINCIPAL_RECEIVABLE);
        assertThat(debit.amount()).isEqualTo(inr(5_000_000));
    }

    @Test
    void initiatedFailedAndAccrualBalance() {
        assertThat(debitsFor("disbursement.initiated", Map.of("net", inr(4_882_000))))
                .isEqualTo(4_882_000L);
        assertThat(debitsFor("disbursement.failed", Map.of("net", inr(4_882_000))))
                .isEqualTo(4_882_000L);
        assertThat(debitsFor("loan.interest-accrued", Map.of("interest", inr(3_288))))
                .isEqualTo(3_288L);
    }

    @Test
    void phase2And3TemplatesAreRegistered() {
        assertThat(registry.handles("disbursement.initiated")).isTrue();
        assertThat(registry.handles("disbursement.succeeded")).isTrue();
        assertThat(registry.handles("disbursement.failed")).isTrue();
        assertThat(registry.handles("loan.interest-accrued")).isTrue();
        // Phase 3 repayment templates.
        assertThat(registry.handles("repayment.received")).isTrue();
        assertThat(registry.handles("loan.repayment-allocated")).isTrue();
        assertThat(registry.handles("repayment.reversed")).isTrue();
        assertThat(registry.handles("loan.repayment-reversed")).isTrue();
        assertThat(registry.handles("repayment.refunded")).isTrue();
        // Phase 4 penal/write-off templates.
        assertThat(registry.handles("loan.penal-accrued")).isTrue();
        assertThat(registry.handles("loan.written-off")).isTrue();
    }

    @Test
    void penalAccruedDebitsPenalReceivableCreditsPenalIncome() {
        var postings =
                registry.find("loan.penal-accrued")
                        .orElseThrow()
                        .build(
                                new PostingInput(
                                        "loan-1", "cust-1", INR, Map.of("penal", inr(329))));
        Posting debit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.DEBIT)
                        .findFirst()
                        .orElseThrow();
        Posting credit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.CREDIT)
                        .findFirst()
                        .orElseThrow();
        assertThat(debit.account()).isEqualTo(AccountCode.PENAL_RECEIVABLE);
        assertThat(credit.account()).isEqualTo(AccountCode.PENAL_INCOME);
        assertThat(debit.amount()).isEqualTo(inr(329));
    }

    @Test
    void writeOffBalancesAndBadDebtTakesPrincipalWhileIncomeIsReversed() {
        Map<String, Money> amounts =
                Map.of(
                        "principal", inr(3_000_000),
                        "interest", inr(200_000),
                        "penal", inr(50_000));
        long total = 3_000_000L + 200_000L + 50_000L;
        assertThat(debitsFor("loan.written-off", amounts)).isEqualTo(total);
        assertThat(creditsFor("loan.written-off", amounts)).isEqualTo(total);

        var postings =
                registry.find("loan.written-off")
                        .orElseThrow()
                        .build(new PostingInput("loan-1", "cust-1", INR, amounts));
        // Bad debt expense (5001) is debited the principal; interest/penal income reversed.
        Posting badDebt =
                postings.stream()
                        .filter(p -> p.account() == AccountCode.BAD_DEBT_WRITTEN_OFF)
                        .findFirst()
                        .orElseThrow();
        assertThat(badDebt.side()).isEqualTo(PostingSide.DEBIT);
        assertThat(badDebt.amount()).isEqualTo(inr(3_000_000));
        assertThat(postings)
                .anyMatch(
                        p ->
                                p.account() == AccountCode.INTEREST_INCOME
                                        && p.side() == PostingSide.DEBIT)
                .anyMatch(
                        p ->
                                p.account() == AccountCode.LOAN_PRINCIPAL_RECEIVABLE
                                        && p.side() == PostingSide.CREDIT);
    }

    @Test
    void writeOffOmitsZeroComponents() {
        Map<String, Money> amounts =
                Map.of("principal", inr(3_000_000), "interest", inr(0), "penal", inr(0));
        var postings =
                registry.find("loan.written-off")
                        .orElseThrow()
                        .build(new PostingInput("loan-1", "cust-1", INR, amounts));
        // Only DR 5001 / CR 1100 remain.
        assertThat(postings).hasSize(2);
        assertThat(debitsFor("loan.written-off", amounts)).isEqualTo(3_000_000L);
    }

    private long creditsFor(String eventType, Map<String, Money> amounts) {
        var template = registry.find(eventType).orElseThrow();
        var postings = template.build(new PostingInput("loan-1", "cust-1", INR, amounts));
        return postings.stream()
                .filter(p -> p.side() == PostingSide.CREDIT)
                .mapToLong(p -> p.amount().minorUnits())
                .sum();
    }

    @Test
    void repaymentReceivedDebitsBankCreditsSuspense() {
        var postings =
                registry.find("repayment.received")
                        .orElseThrow()
                        .build(
                                new PostingInput(
                                        "loan-1", "cust-1", INR, Map.of("amount", inr(474_000))));
        Posting debit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.DEBIT)
                        .findFirst()
                        .orElseThrow();
        Posting credit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.CREDIT)
                        .findFirst()
                        .orElseThrow();
        assertThat(debit.account()).isEqualTo(AccountCode.BANK_OPERATING);
        assertThat(credit.account()).isEqualTo(AccountCode.CUSTOMER_SUSPENSE);
        assertThat(debit.amount()).isEqualTo(inr(474_000));
    }

    @Test
    void allocatedSplitsSuspenseAcrossComponentsAndBalances() {
        Map<String, Money> amounts =
                Map.of(
                        "total", inr(474_000),
                        "penal", inr(0),
                        "interest", inr(100_000),
                        "principal", inr(374_000));
        assertThat(debitsFor("loan.repayment-allocated", amounts)).isEqualTo(474_000L);
        assertThat(creditsFor("loan.repayment-allocated", amounts)).isEqualTo(474_000L);
        // zero penal component is omitted: two credit lines (interest, principal) + one debit.
        var postings =
                registry.find("loan.repayment-allocated")
                        .orElseThrow()
                        .build(new PostingInput("loan-1", "cust-1", INR, amounts));
        assertThat(postings).hasSize(3);
    }

    @Test
    void reversalsMirrorTheReceiptAndAllocation() {
        assertThat(debitsFor("repayment.reversed", Map.of("amount", inr(474_000))))
                .isEqualTo(474_000L);
        assertThat(creditsFor("repayment.reversed", Map.of("amount", inr(474_000))))
                .isEqualTo(474_000L);

        Map<String, Money> alloc =
                Map.of(
                        "total", inr(474_000),
                        "penal", inr(0),
                        "interest", inr(100_000),
                        "principal", inr(374_000));
        assertThat(debitsFor("loan.repayment-reversed", alloc)).isEqualTo(474_000L);
        assertThat(creditsFor("loan.repayment-reversed", alloc)).isEqualTo(474_000L);
    }

    @Test
    void overpaymentRefundDebitsSuspenseCreditsBank() {
        var postings =
                registry.find("repayment.refunded")
                        .orElseThrow()
                        .build(
                                new PostingInput(
                                        "loan-1", "cust-1", INR, Map.of("amount", inr(5_000))));
        Posting debit =
                postings.stream()
                        .filter(p -> p.side() == PostingSide.DEBIT)
                        .findFirst()
                        .orElseThrow();
        assertThat(debit.account()).isEqualTo(AccountCode.CUSTOMER_SUSPENSE);
        assertThat(creditsFor("repayment.refunded", Map.of("amount", inr(5_000))))
                .isEqualTo(5_000L);
    }
}
