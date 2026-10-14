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
    void onlyThePhase2TemplatesAreRegistered() {
        assertThat(registry.handles("disbursement.initiated")).isTrue();
        assertThat(registry.handles("disbursement.succeeded")).isTrue();
        assertThat(registry.handles("disbursement.failed")).isTrue();
        assertThat(registry.handles("loan.interest-accrued")).isTrue();
        // Repayment/penal/write-off arrive in Phases 3-4.
        assertThat(registry.handles("loan.repayment-allocated")).isFalse();
    }
}
