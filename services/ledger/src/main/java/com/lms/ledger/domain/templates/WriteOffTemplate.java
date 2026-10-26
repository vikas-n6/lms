package com.lms.ledger.domain.templates;

import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import com.lms.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Write-off template (phase prompt task 3, docs/domain-rules.md "Posting templates"):
 *
 * <pre>
 *   loan.written-off : DR 5001 Bad debt (principal), 4001 Interest income, 4003 Penal income
 *                      CR 1100 principal, 1110 interest, 1130 penal receivables
 * </pre>
 *
 * The bad debt expense takes the written-off <b>principal</b>; the unpaid accrued interest and
 * penal are removed by <b>reversing</b> the income previously recognised (debit 4001/4003), and all
 * three receivables are credited to zero. Debits (principal + interest + penal) equal credits, so
 * the entry balances; the net P&amp;L impact of the write-off is the full outstanding total. Zero
 * components are omitted.
 */
public final class WriteOffTemplate implements PostingTemplate {

    @Override
    public String eventType() {
        return "loan.written-off";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<Posting> build(PostingInput in) {
        Money principal = in.amount("principal");
        Money interest = in.amount("interest");
        Money penal = in.amount("penal");

        List<Posting> postings = new ArrayList<>();
        // Debits: bad-debt expense for principal; reverse interest/penal income.
        addDebit(postings, in, AccountCode.BAD_DEBT_WRITTEN_OFF, principal);
        addDebit(postings, in, AccountCode.INTEREST_INCOME, interest);
        addDebit(postings, in, AccountCode.PENAL_INCOME, penal);
        // Credits: clear the receivables.
        addCredit(postings, in, AccountCode.LOAN_PRINCIPAL_RECEIVABLE, principal);
        addCredit(postings, in, AccountCode.INTEREST_RECEIVABLE, interest);
        addCredit(postings, in, AccountCode.PENAL_RECEIVABLE, penal);
        return List.copyOf(postings);
    }

    private static void addDebit(
            List<Posting> postings, PostingInput in, AccountCode account, Money amount) {
        if (!amount.isZero()) {
            postings.add(Posting.debit(account, amount, in.loanId(), in.customerId()));
        }
    }

    private static void addCredit(
            List<Posting> postings, PostingInput in, AccountCode account, Money amount) {
        if (!amount.isZero()) {
            postings.add(Posting.credit(account, amount, in.loanId(), in.customerId()));
        }
    }
}
