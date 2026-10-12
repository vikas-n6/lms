package com.lms.origination.adapters.in.schedule;

import com.lms.origination.application.OriginationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically expires offers past their 48h window, emitting {@code application.expired} (phase
 * prompt S4). Interval is configurable; the business decision (expiry instant) is driven by the
 * injected Clock inside the service, not by the schedule tick.
 */
@Component
public class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final OriginationService origination;

    public OfferExpiryJob(OriginationService origination) {
        this.origination = origination;
    }

    @Scheduled(fixedDelayString = "${lms.origination.expiry-scan-ms:60000}")
    public void expire() {
        int expired = origination.expireOffers();
        if (expired > 0) {
            log.info("expired {} offer(s)", expired);
        }
    }
}
