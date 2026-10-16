package com.lms.ledger.domain.templates;

import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import com.lms.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Repayment posting templates (docs/domain-rules.md "Posting templates"):
 *
 * <pre>
 *   repayment.received       : DR 1001 Bank              / CR 2200 Customer suspense    (amount)
 *   loan.repayment-allocated : DR 2200 Customer suspense / CR 1130 penal, 1110 interest, 1100 principal
 *   repayment.reversed       : DR 2200 Customer suspense / CR 1001 Bank                 (reverses receipt)
 *   loan.repayment-reversed  : DR 1130/1110/1100         / CR 2200 Customer suspense    (reverses allocation)
 *   overpayment refund       : DR 2200 Customer suspense / CR 1001 Bank                 (amount)
 * </pre>
 *
 * The allocation (and its reversal) splits Customer suspense across the components; the component
 * total equals the suspense leg, so each entry balances. Zero components are omitted.
 */
public final class RepaymentTemplates {

    private RepaymentTemplates() {}

    /** DR Bank / CR Customer suspense for the received amount. */
    public static final class Received implements PostingTemplate {
        @Override
        public String eventType() {
            return "repayment.received";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            Money amount = in.amount("amount");
            return List.of(
                    Posting.debit(AccountCode.BANK_OPERATING, amount, in.loanId(), in.customerId()),
                    Posting.credit(
                            AccountCode.CUSTOMER_SUSPENSE, amount, in.loanId(), in.customerId()));
        }
    }

    /** DR Customer suspense (total) / CR penal + interest + principal receivables. */
    public static final class Allocated implements PostingTemplate {
        @Override
        public String eventType() {
            return "loan.repayment-allocated";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            Money total = in.amount("total");
            List<Posting> postings = new ArrayList<>();
            postings.add(
                    Posting.debit(
                            AccountCode.CUSTOMER_SUSPENSE, total, in.loanId(), in.customerId()));
            addCredit(postings, in, AccountCode.PENAL_RECEIVABLE, in.amount("penal"));
            addCredit(postings, in, AccountCode.INTEREST_RECEIVABLE, in.amount("interest"));
            addCredit(postings, in, AccountCode.LOAN_PRINCIPAL_RECEIVABLE, in.amount("principal"));
            return List.copyOf(postings);
        }

        private static void addCredit(
                List<Posting> postings, PostingInput in, AccountCode account, Money amount) {
            if (!amount.isZero()) {
                postings.add(Posting.credit(account, amount, in.loanId(), in.customerId()));
            }
        }
    }

    /** DR Customer suspense / CR Bank — reverses a receipt (a returned debit). */
    public static final class Reversed implements PostingTemplate {
        @Override
        public String eventType() {
            return "repayment.reversed";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            Money amount = in.amount("amount");
            return List.of(
                    Posting.debit(
                            AccountCode.CUSTOMER_SUSPENSE, amount, in.loanId(), in.customerId()),
                    Posting.credit(
                            AccountCode.BANK_OPERATING, amount, in.loanId(), in.customerId()));
        }
    }

    /** DR penal + interest + principal / CR Customer suspense — reverses an allocation. */
    public static final class AllocationReversed implements PostingTemplate {
        @Override
        public String eventType() {
            return "loan.repayment-reversed";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            Money total = in.amount("total");
            List<Posting> postings = new ArrayList<>();
            addDebit(postings, in, AccountCode.PENAL_RECEIVABLE, in.amount("penal"));
            addDebit(postings, in, AccountCode.INTEREST_RECEIVABLE, in.amount("interest"));
            addDebit(postings, in, AccountCode.LOAN_PRINCIPAL_RECEIVABLE, in.amount("principal"));
            postings.add(
                    Posting.credit(
                            AccountCode.CUSTOMER_SUSPENSE, total, in.loanId(), in.customerId()));
            return List.copyOf(postings);
        }

        private static void addDebit(
                List<Posting> postings, PostingInput in, AccountCode account, Money amount) {
            if (!amount.isZero()) {
                postings.add(Posting.debit(account, amount, in.loanId(), in.customerId()));
            }
        }
    }

    /** DR Customer suspense / CR Bank — refund of advance held in suspense. */
    public static final class OverpaymentRefund implements PostingTemplate {
        @Override
        public String eventType() {
            return "repayment.refunded";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            Money amount = in.amount("amount");
            return List.of(
                    Posting.debit(
                            AccountCode.CUSTOMER_SUSPENSE, amount, in.loanId(), in.customerId()),
                    Posting.credit(
                            AccountCode.BANK_OPERATING, amount, in.loanId(), in.customerId()));
        }
    }
}
