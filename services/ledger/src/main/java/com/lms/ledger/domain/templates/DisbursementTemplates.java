package com.lms.ledger.domain.templates;

import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import java.util.List;

/**
 * Disbursement posting templates (docs/domain-rules.md "Posting templates"):
 *
 * <pre>
 *   disbursement.initiated : DR 2300 net           / CR 1001 net
 *   disbursement.succeeded : DR 1100 principal      / CR 2300 net, 4002 fee, 2100 gst
 *   disbursement.failed    : DR 1001 net            / CR 2300 net
 * </pre>
 *
 * Net = principal − fee − gst, so the {@code succeeded} entry balances.
 */
public final class DisbursementTemplates {

    private DisbursementTemplates() {}

    /** DR Disbursement-in-transit / CR Bank for the net amount. */
    public static final class Initiated implements PostingTemplate {
        @Override
        public String eventType() {
            return "disbursement.initiated";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            var net = in.amount("net");
            return List.of(
                    Posting.debit(
                            AccountCode.DISBURSEMENT_IN_TRANSIT, net, in.loanId(), in.customerId()),
                    Posting.credit(AccountCode.BANK_OPERATING, net, in.loanId(), in.customerId()));
        }
    }

    /**
     * DR Principal receivable / CR Disbursement-in-transit + Processing fee income + GST payable.
     */
    public static final class Succeeded implements PostingTemplate {
        @Override
        public String eventType() {
            return "disbursement.succeeded";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            var principal = in.amount("principal");
            var net = in.amount("net");
            var fee = in.amount("fee");
            var gst = in.amount("gst");
            return List.of(
                    Posting.debit(
                            AccountCode.LOAN_PRINCIPAL_RECEIVABLE,
                            principal,
                            in.loanId(),
                            in.customerId()),
                    Posting.credit(
                            AccountCode.DISBURSEMENT_IN_TRANSIT, net, in.loanId(), in.customerId()),
                    Posting.credit(
                            AccountCode.PROCESSING_FEE_INCOME, fee, in.loanId(), in.customerId()),
                    Posting.credit(AccountCode.GST_PAYABLE, gst, in.loanId(), in.customerId()));
        }
    }

    /** DR Bank / CR Disbursement-in-transit for the net amount (compensates the initiation). */
    public static final class Failed implements PostingTemplate {
        @Override
        public String eventType() {
            return "disbursement.failed";
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public List<Posting> build(PostingInput in) {
            var net = in.amount("net");
            return List.of(
                    Posting.debit(AccountCode.BANK_OPERATING, net, in.loanId(), in.customerId()),
                    Posting.credit(
                            AccountCode.DISBURSEMENT_IN_TRANSIT,
                            net,
                            in.loanId(),
                            in.customerId()));
        }
    }
}
