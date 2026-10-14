package com.lms.loanaccount.adapters.in.jobs;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.AccrueInterestService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the daily interest-accrual job on the business date (docs/architecture.md: all jobs use the
 * {@link BusinessCalendar}, never the system clock directly). Idempotent per {@code (loanId,
 * businessDate)}, so a re-run is harmless; the {@code /admin/jobs/interest-accrual/run} endpoint
 * drives the same service by an explicit date for testing.
 */
@Component
public class ScheduledAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAccrualJob.class);

    private final AccrueInterestService accrueService;
    private final BusinessCalendar calendar;

    public ScheduledAccrualJob(AccrueInterestService accrueService, BusinessCalendar calendar) {
        this.accrueService = Objects.requireNonNull(accrueService);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Scheduled(cron = "${lms.jobs.interest-accrual-cron:0 30 0 * * *}")
    public void run() {
        int recorded = accrueService.runFor(calendar.today());
        log.info("Interest accrual for {} recorded {} loan(s)", calendar.today(), recorded);
    }
}
