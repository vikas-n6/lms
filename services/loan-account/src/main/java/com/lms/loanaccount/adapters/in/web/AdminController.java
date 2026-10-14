package com.lms.loanaccount.adapters.in.web;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.AccrueInterestService;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints. The {@code jobs/{name}/run} trigger drives the scheduled jobs by business date
 * for testing (phase prompt task 1); all jobs use the {@link BusinessCalendar}, never the system
 * clock.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AccrueInterestService accrueService;
    private final BusinessCalendar calendar;

    public AdminController(AccrueInterestService accrueService, BusinessCalendar calendar) {
        this.accrueService = Objects.requireNonNull(accrueService);
        this.calendar = Objects.requireNonNull(calendar);
    }

    /** Run a named scheduled job for a business date (defaults to the current business date). */
    @PostMapping("/jobs/{name}/run")
    public Map<String, Object> runJob(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate businessDate = date != null ? date : calendar.today();
        if (!"interest-accrual".equals(name)) {
            throw new IllegalArgumentException("unknown job: " + name);
        }
        int recorded = accrueService.runFor(businessDate);
        return Map.of("job", name, "date", businessDate.toString(), "recorded", recorded);
    }
}
