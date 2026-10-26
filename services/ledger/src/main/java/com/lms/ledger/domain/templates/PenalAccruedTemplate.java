package com.lms.ledger.domain.templates;

import com.lms.ledger.domain.AccountCode;
import com.lms.ledger.domain.Posting;
import com.lms.ledger.domain.PostingInput;
import com.lms.ledger.domain.PostingTemplate;
import java.util.List;

/**
 * Daily penal accrual template (phase prompt task 3, docs/domain-rules.md "Posting templates"): DR
 * Penal receivable / CR Penal income for the day's accrued penal interest.
 */
public final class PenalAccruedTemplate implements PostingTemplate {

    @Override
    public String eventType() {
        return "loan.penal-accrued";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<Posting> build(PostingInput in) {
        var amount = in.amount("penal");
        return List.of(
                Posting.debit(AccountCode.PENAL_RECEIVABLE, amount, in.loanId(), in.customerId()),
                Posting.credit(AccountCode.PENAL_INCOME, amount, in.loanId(), in.customerId()));
    }
}
