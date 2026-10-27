package com.lms.reporting.domain;

import com.lms.money.Money;

/**
 * Collection efficiency (phase prompt task 1): the share of what was billed that was collected, in
 * basis points. Reporting is event-sourced, so "billed" accumulates the instalment amounts that
 * fell due (from {@code loan.installment-due-soon}) and the penal charged ({@code
 * loan.penal-accrued}), and "collected" accumulates the allocation totals ({@code
 * loan.repayment-allocated}). This is a documented approximation of the classic
 * demand-vs-collection measure built purely from the event stream (ADR 0013).
 */
public record CollectionEfficiency(Money billed, Money collected, long efficiencyBps) {

    public static CollectionEfficiency of(Money billed, Money collected) {
        return new CollectionEfficiency(billed, collected, Ratios.basisPoints(collected, billed));
    }
}
