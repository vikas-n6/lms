package com.lms.ledger.adapters.in.jobs;

import com.lms.ledger.application.BankRailReconciliationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily bank/rail reconciliation job (phase prompt task 6). Runs for the current business date in
 * {@code Asia/Kolkata}; the admin endpoint drives the same service for an explicit date in tests
 * and the e2e script.
 */
@Component
public class BankRailReconJob {

    private static final Logger log = LoggerFactory.getLogger(BankRailReconJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BankRailReconciliationService reconciliation;
    private final Clock clock;

    public BankRailReconJob(BankRailReconciliationService reconciliation, Clock clock) {
        this.reconciliation = Objects.requireNonNull(reconciliation);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(cron = "${lms.jobs.bank-rail-recon-cron:0 15 1 * * *}")
    public void run() {
        LocalDate date = LocalDate.ofInstant(clock.instant(), IST);
        UUID runId = reconciliation.reconcile(date);
        log.info("bank/rail reconciliation for {} -> run {}", date, runId);
    }
}
