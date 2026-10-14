package com.lms.ledger.adapters.in.jobs;

import com.lms.ledger.application.ReconciliationService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trial-balance integrity check (phase prompt task 3). Each run exports the current diff
 * as the gauge {@code ledger_trial_balance_diff_minor_units}; a non-zero diff also publishes an
 * alert event (via {@link ReconciliationService}). The gauge stays at the last observed value.
 */
@Component
public class LedgerReconJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconJob.class);

    private final ReconciliationService reconciliation;
    private final AtomicLong diffGauge = new AtomicLong(0L);

    public LedgerReconJob(ReconciliationService reconciliation, MeterRegistry registry) {
        this.reconciliation = Objects.requireNonNull(reconciliation);
        Gauge.builder("ledger_trial_balance_diff_minor_units", diffGauge, AtomicLong::get)
                .description("Trial-balance debit-credit diff in minor units; must be zero")
                .register(registry);
    }

    @Scheduled(cron = "${lms.jobs.reconciliation-cron:0 0 * * * *}")
    public void run() {
        long diff = reconciliation.reconcile();
        diffGauge.set(diff);
        if (diff != 0L) {
            log.warn("Ledger trial balance does not net to zero: diff={} minor units", diff);
        }
    }
}
