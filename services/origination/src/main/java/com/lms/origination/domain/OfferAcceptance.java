package com.lms.origination.domain;

import com.lms.schedule.OfferQuote;

/**
 * The outcome of attempting to accept an offer (phase prompt S4): either the selected option (offer
 * live, tenor offered, OTP verified) or a typed reason it was refused. Pure; the caller turns a
 * refusal into an error response and a success into {@code application.accepted}.
 */
public sealed interface OfferAcceptance {

    record Accepted(OfferQuote option) implements OfferAcceptance {}

    record Refused(Reason reason) implements OfferAcceptance {}

    enum Reason {
        OFFER_EXPIRED,
        TENOR_NOT_OFFERED,
        OTP_INVALID
    }

    static OfferAcceptance accepted(OfferQuote option) {
        return new Accepted(option);
    }

    static OfferAcceptance refused(Reason reason) {
        return new Refused(reason);
    }
}
