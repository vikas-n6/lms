package com.lms.decision.application.port.out;

import com.lms.money.Money;

/** Outbound port to the bank statement-analysis simulator (estimated monthly income). */
public interface BankClient {

    /** Estimated monthly income from statement analysis, used as a cross-check input. */
    Money estimatedMonthlyIncome(String subjectId);
}
