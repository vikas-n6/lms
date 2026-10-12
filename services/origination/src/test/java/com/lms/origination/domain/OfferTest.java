package com.lms.origination.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.OfferQuote;
import com.lms.schedule.Product;
import com.lms.schedule.ReducingBalanceScheduleEngine;
import com.lms.schedule.ScheduleEngine;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Instant OFFERED = Instant.parse("2026-10-12T10:00:00Z");
    private final ScheduleEngine engine = new ReducingBalanceScheduleEngine();

    private static Product product() {
        return new FixedTenorReducingBalanceProduct(
                "MICRO-V1",
                1,
                INR,
                Money.of(500_000L, INR),
                Money.of(5_000_000L, INR),
                Money.of(50_000L, INR),
                List.of(3, 6, 9, 12),
                Rate.percent(24),
                Rate.percent(2),
                Rate.percent(18),
                Rate.percent(36),
                3,
                Rate.zero());
    }

    private Offer offer() {
        List<OfferQuote> options =
                List.of(
                        engine.quote(
                                product(),
                                Money.of(5_000_000L, INR),
                                6,
                                LocalDate.of(2026, 10, 12)),
                        engine.quote(
                                product(),
                                Money.of(5_000_000L, INR),
                                12,
                                LocalDate.of(2026, 10, 12)));
        return Offer.create(options, OFFERED, Duration.ofHours(48));
    }

    @Test
    void expiresFortyEightHoursAfterOffering() {
        Offer offer = offer();
        assertThat(offer.expiresAt()).isEqualTo(OFFERED.plus(Duration.ofHours(48)));
        assertThat(offer.isExpired(OFFERED.plus(Duration.ofHours(47)))).isFalse();
        assertThat(offer.isExpired(OFFERED.plus(Duration.ofHours(48)))).isTrue();
    }

    @Test
    void acceptSucceedsForALiveOfferedTenorWithValidOtp() {
        OfferAcceptance result = offer().accept(12, true, OFFERED.plus(Duration.ofHours(1)));
        assertThat(result).isInstanceOf(OfferAcceptance.Accepted.class);
        assertThat(((OfferAcceptance.Accepted) result).option().tenor()).isEqualTo(12);
    }

    @Test
    void acceptRefusesExpiredThenTenorThenOtpInThatOrder() {
        Offer offer = offer();
        // Expired dominates even with a valid tenor + OTP.
        assertThat(refusal(offer.accept(12, true, OFFERED.plus(Duration.ofHours(49)))))
                .isEqualTo(OfferAcceptance.Reason.OFFER_EXPIRED);
        // Live but tenor not offered.
        assertThat(refusal(offer.accept(3, true, OFFERED.plus(Duration.ofHours(1)))))
                .isEqualTo(OfferAcceptance.Reason.TENOR_NOT_OFFERED);
        // Live, offered tenor, but OTP invalid.
        assertThat(refusal(offer.accept(12, false, OFFERED.plus(Duration.ofHours(1)))))
                .isEqualTo(OfferAcceptance.Reason.OTP_INVALID);
    }

    private static OfferAcceptance.Reason refusal(OfferAcceptance acceptance) {
        return ((OfferAcceptance.Refused) acceptance).reason();
    }
}
