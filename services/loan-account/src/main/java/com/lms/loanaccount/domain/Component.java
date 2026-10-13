package com.lms.loanaccount.domain;

/**
 * A repayable component of an instalment. The allocation waterfall settles them in this order
 * within an instalment: penal, then interest, then principal (docs/domain-rules.md "Repayment
 * allocation waterfall"). The declaration order is the waterfall order.
 */
public enum Component {
    PENAL,
    INTEREST,
    PRINCIPAL
}
