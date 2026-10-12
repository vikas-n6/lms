package com.lms.origination.adapters.in.web;

import com.lms.origination.domain.AcceptedTerms;
import com.lms.origination.domain.Application;
import com.lms.schedule.OfferQuote;
import java.time.Instant;
import java.util.List;

/**
 * Application response for the back-office UI. Omits raw PII (phone, DOB); surfaces status, the
 * offer options and the accepted terms.
 */
public record ApplicationResponse(
        String id,
        String customerId,
        String status,
        long requestedAmountMinor,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<OfferOptionView> offerOptions,
        Instant offerExpiresAt,
        AcceptedTermsView acceptedTerms) {

    public record OfferOptionView(
            int tenorMonths,
            long emiMinor,
            long feeMinor,
            long gstMinor,
            long netDisbursalMinor,
            long totalPayableMinor,
            String effectiveAnnualRatePercent) {}

    public record AcceptedTermsView(
            int tenorMonths,
            long principalMinor,
            long emiMinor,
            long netDisbursalMinor,
            String effectiveAnnualRatePercent,
            String esignReference) {}

    public static ApplicationResponse from(Application a) {
        List<OfferOptionView> options =
                a.offer()
                        .map(
                                offer ->
                                        offer.options().stream()
                                                .map(ApplicationResponse::option)
                                                .toList())
                        .orElse(List.of());
        AcceptedTermsView terms = a.acceptedTerms().map(ApplicationResponse::terms).orElse(null);
        return new ApplicationResponse(
                a.id().toString(),
                a.customerId(),
                a.status().code(),
                a.requestedAmount().minorUnits(),
                a.requestedAmount().currency().getCurrencyCode(),
                a.createdAt(),
                a.updatedAt(),
                options,
                a.offer().map(o -> o.expiresAt()).orElse(null),
                terms);
    }

    private static OfferOptionView option(OfferQuote q) {
        return new OfferOptionView(
                q.tenor(),
                q.emi().minorUnits(),
                q.fee().minorUnits(),
                q.gst().minorUnits(),
                q.netDisbursal().minorUnits(),
                q.totalPayable().minorUnits(),
                q.effectiveAnnualRate().asPlainString());
    }

    private static AcceptedTermsView terms(AcceptedTerms t) {
        return new AcceptedTermsView(
                t.tenorMonths(),
                t.principal().minorUnits(),
                t.emi().minorUnits(),
                t.netDisbursal().minorUnits(),
                t.effectiveAnnualRatePercent(),
                t.esignReference());
    }
}
