package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;

/**
 * Produces amortisation schedules and priced offer quotes (docs/domain-rules.md "Schedule engine").
 * Behind an interface so the product family can evolve (CLAUDE.md locked decisions).
 */
public interface ScheduleEngine {

    /**
     * Build the reducing-balance schedule for a raw principal/rate/tenor anchored at {@code
     * anchorDate}.
     *
     * @param provisional true for an indicative schedule priced before disbursement
     */
    Schedule generate(
            Money principal, Rate annualRate, int tenor, LocalDate anchorDate, boolean provisional);

    /**
     * Price a full offer option for one of the product's tenors: schedule, processing fee, GST on
     * the fee, net disbursal and the effective APR implied by the cash flows.
     */
    OfferQuote quote(Product product, Money principal, int tenor, LocalDate anchorDate);
}
