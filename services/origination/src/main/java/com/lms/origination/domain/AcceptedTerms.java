package com.lms.origination.domain;

import com.lms.money.Money;
import com.lms.schedule.OfferQuote;
import java.util.Objects;

/**
 * The terms the customer accepted (phase prompt S4): the chosen option plus the OTP e-sign
 * reference. Carried in {@code application.accepted} for loan-account (Phase 2).
 */
public record AcceptedTerms(
        int tenorMonths,
        Money principal,
        Money emi,
        Money fee,
        Money gst,
        Money netDisbursal,
        String effectiveAnnualRatePercent,
        String esignReference) {

    public AcceptedTerms {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(esignReference, "esignReference");
    }

    public static AcceptedTerms from(OfferQuote option, String esignReference) {
        return new AcceptedTerms(
                option.tenor(),
                option.principal(),
                option.emi(),
                option.fee(),
                option.gst(),
                option.netDisbursal(),
                option.effectiveAnnualRate().asPlainString(),
                esignReference);
    }
}
