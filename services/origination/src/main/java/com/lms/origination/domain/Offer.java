package com.lms.origination.domain;

import com.lms.schedule.OfferQuote;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An offer: one priced {@link OfferQuote} per tenor, with an expiry (default 48h after offering,
 * domain-rules). The customer selects a tenor at acceptance, which must happen before expiry.
 *
 * @param options one priced option per tenor
 * @param offeredAt when the offer was created (UTC)
 * @param expiresAt when the offer lapses (UTC)
 */
public record Offer(List<OfferQuote> options, Instant offeredAt, Instant expiresAt) {

    public Offer {
        Objects.requireNonNull(offeredAt, "offeredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("an offer needs at least one option");
        }
        options = List.copyOf(options);
        if (!expiresAt.isAfter(offeredAt)) {
            throw new IllegalArgumentException("expiresAt must be after offeredAt");
        }
    }

    /** Create an offer that lapses {@code ttl} after {@code offeredAt} (ttl from configuration). */
    public static Offer create(List<OfferQuote> options, Instant offeredAt, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return new Offer(options, offeredAt, offeredAt.plus(ttl));
    }

    /** True when {@code now} is at or after the expiry instant. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** The option for a tenor, if offered. */
    public Optional<OfferQuote> optionFor(int tenor) {
        return options.stream().filter(o -> o.tenor() == tenor).findFirst();
    }

    /**
     * Evaluate an acceptance: the offer must be live, the tenor offered and the OTP e-sign valid.
     * Checks run in that order so the most fundamental refusal is reported first.
     */
    public OfferAcceptance accept(int tenor, boolean otpValid, Instant now) {
        if (isExpired(now)) {
            return OfferAcceptance.refused(OfferAcceptance.Reason.OFFER_EXPIRED);
        }
        Optional<OfferQuote> option = optionFor(tenor);
        if (option.isEmpty()) {
            return OfferAcceptance.refused(OfferAcceptance.Reason.TENOR_NOT_OFFERED);
        }
        if (!otpValid) {
            return OfferAcceptance.refused(OfferAcceptance.Reason.OTP_INVALID);
        }
        return OfferAcceptance.accepted(option.get());
    }
}
