package com.lms.reporting.adapters.in.jobs;

import com.lms.reporting.application.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Captures the daily portfolio snapshot on a fixed cadence. The snapshot is keyed by the business
 * date and upserted, so repeated runs on the same business date are idempotent; the series is still
 * fully reconstructable from the read models (which are themselves rebuildable from offset 0).
 */
@Component
public class ScheduledSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledSnapshotJob.class);

    private final SnapshotService snapshots;

    public ScheduledSnapshotJob(SnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Scheduled(cron = "${lms.reporting.snapshot-cron:0 30 0 * * *}", zone = "Asia/Kolkata")
    public void capture() {
        var snapshot = snapshots.captureDaily();
        log.info(
                "captured daily snapshot for {}: {} loans, {} active",
                snapshot.businessDate(),
                snapshot.totalLoans(),
                snapshot.activeLoans());
    }
}
