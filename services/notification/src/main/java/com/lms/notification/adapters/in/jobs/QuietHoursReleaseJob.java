package com.lms.notification.adapters.in.jobs;

import com.lms.notification.application.NotificationService;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases notifications held for quiet hours once the window has ended (phase prompt task 5: "held
 * messages are released in the morning"). Runs frequently; each run delivers only messages whose
 * release time has passed, so it is safe to re-run.
 */
@Component
public class QuietHoursReleaseJob {

    private static final Logger log = LoggerFactory.getLogger(QuietHoursReleaseJob.class);

    private final NotificationService service;
    private final Clock clock;

    public QuietHoursReleaseJob(NotificationService service, Clock clock) {
        this.service = Objects.requireNonNull(service);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(cron = "${lms.notification.release-cron:0 */5 * * * *}")
    public void run() {
        int released = service.releaseDue(clock.instant());
        if (released > 0) {
            log.info("released {} held notification(s)", released);
        }
    }
}
