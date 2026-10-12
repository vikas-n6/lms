package com.lms.origination.application;

import com.lms.money.Money;
import com.lms.schedule.OfferQuote;
import com.lms.schedule.Product;
import com.lms.schedule.ScheduleEngine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Prices an offer from a decision's approved limit (phase prompt S4). For each of the product's
 * tenors it produces an {@link OfferQuote} (EMI, fee, GST, net disbursal, effective APR) via the
 * schedule engine. Pure: the caller supplies the anchor date from the injected Clock.
 */
public final class OfferPricing {

    private final ScheduleEngine scheduleEngine;

    public OfferPricing(ScheduleEngine scheduleEngine) {
        this.scheduleEngine = scheduleEngine;
    }

    /**
     * Price one option per offered tenor, using the largest supported principal not exceeding the
     * approved limit. Returns empty when the approved limit is below the product minimum.
     */
    public Optional<List<OfferQuote>> price(
            Product product, Money approvedLimit, LocalDate anchorDate) {
        return product.largestSupportedAtMost(approvedLimit)
                .map(
                        principal ->
                                product.tenors().stream()
                                        .sorted()
                                        .map(
                                                tenor ->
                                                        scheduleEngine.quote(
                                                                product,
                                                                principal,
                                                                tenor,
                                                                anchorDate))
                                        .toList());
    }
}
