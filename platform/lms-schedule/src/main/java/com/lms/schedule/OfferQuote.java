package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Objects;

/**
 * A priced offer option for one tenor (docs/domain-rules.md "The one product"). Origination
 * presents one of these per tenor (3, 6, 9, 12). The identity {@code netDisbursal + fee + gst ==
 * principal} always holds, which keeps the ledger's disbursement entry balanced.
 *
 * @param principal financed principal
 * @param tenor instalment count
 * @param emi level EMI
 * @param fee processing fee (deducted up front)
 * @param gst GST charged on the fee only
 * @param netDisbursal cash actually paid out = {@code principal - fee - gst}
 * @param effectiveAnnualRate APR implied by the net-disbursal / EMI cash flows (not the nominal
 *     rate)
 * @param schedule the full amortisation schedule backing this quote
 */
public record OfferQuote(
        Money principal,
        int tenor,
        Money emi,
        Money fee,
        Money gst,
        Money netDisbursal,
        Rate effectiveAnnualRate,
        Schedule schedule) {

    public OfferQuote {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(emi, "emi");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(netDisbursal, "netDisbursal");
        Objects.requireNonNull(effectiveAnnualRate, "effectiveAnnualRate");
        Objects.requireNonNull(schedule, "schedule");
        // Invariant that keeps the disbursement ledger entry balanced.
        if (!netDisbursal.add(fee).add(gst).equals(principal)) {
            throw new IllegalArgumentException("netDisbursal + fee + gst must equal principal");
        }
    }

    public Money totalPayable() {
        return schedule.totalPayable();
    }
}
