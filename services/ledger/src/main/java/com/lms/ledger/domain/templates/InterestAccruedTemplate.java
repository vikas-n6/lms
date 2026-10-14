package com.lms.ledger.domain.templates;

import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import java.util.List;

/**
 * Daily interest accrual template (docs/domain-rules.md "Posting templates"): DR Interest
 * receivable / CR Interest income for the day's recognised interest.
 */
public final class InterestAccruedTemplate implements PostingTemplate {

    @Override
    public String eventType() {
        return "loan.interest-accrued";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<Posting> build(PostingInput in) {
        var amount = in.amount("interest");
        return List.of(
                Posting.debit(
                        AccountCode.INTEREST_RECEIVABLE, amount, in.loanId(), in.customerId()),
                Posting.credit(AccountCode.INTEREST_INCOME, amount, in.loanId(), in.customerId()));
    }
}
