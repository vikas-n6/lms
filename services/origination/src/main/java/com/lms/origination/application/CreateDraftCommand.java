package com.lms.origination.application;

import com.lms.money.Money;
import java.time.LocalDate;

/** Input to create an application draft (back-office wizard). Money is built by the web adapter. */
public record CreateDraftCommand(
        String customerId,
        String productId,
        Money requestedAmount,
        String phone,
        LocalDate dateOfBirth,
        Money declaredMonthlyIncome,
        String employmentType,
        boolean dataProcessingConsent,
        boolean creditPullConsent,
        boolean loanAgreementConsent) {}
