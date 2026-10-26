package com.lms.loanaccount.adapters.in.jobs;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.DailyLoanJobs;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the loan-account daily jobs on the business date (docs/architecture.md: all jobs use the
 * {@link BusinessCalendar}, never the system clock directly). The jobs run in the order the phase
 * prompt fixes (mark due/overdue [derived] → interest → penal → DPD → due-soon) and each is
 * idempotent per {@code (loanId, businessDate)}, so a re-run is harmless; the {@code
 * /admin/jobs/{name}/run} endpoint drives the same services by an explicit date for testing.
 */
@Component
public class ScheduledAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAccrualJob.class);

    private final DailyLoanJobs dailyJobs;
    private final BusinessCalendar calendar;

    public ScheduledAccrualJob(DailyLoanJobs dailyJobs, BusinessCalendar calendar) {
        this.dailyJobs = Objects.requireNonNull(dailyJobs);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Scheduled(cron = "${lms.jobs.daily-cron:0 30 0 * * *}")
    public void run() {
        var today = calendar.today();
        DailyLoanJobs.Result r = dailyJobs.runFor(today);
        log.info(
                "Daily loan jobs for {}: interest={} penal={} dpd={} dueSoon={}",
                today,
                r.interest(),
                r.penal(),
                r.dpd(),
                r.dueSoon());
    }
}
