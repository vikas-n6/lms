package com.lms.loanaccount.adapters.in.web;

import com.lms.config.BusinessCalendar;
import com.lms.loanaccount.application.AccrueInterestService;
import com.lms.loanaccount.application.DailyLoanJobs;
import com.lms.loanaccount.application.DpdService;
import com.lms.loanaccount.application.DueSoonService;
import com.lms.loanaccount.application.PenalAccrualService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints. The {@code jobs/{name}/run} trigger drives the scheduled daily jobs by business
 * date for testing (phase prompt task 1); all jobs use the {@link BusinessCalendar}, never the
 * system clock. {@code daily} runs the whole sequence in order (mark due/overdue [derived] →
 * interest → penal → DPD → due-soon); the individual names run one step for targeted tests.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DailyLoanJobs dailyJobs;
    private final AccrueInterestService interest;
    private final PenalAccrualService penal;
    private final DpdService dpd;
    private final DueSoonService dueSoon;
    private final BusinessCalendar calendar;

    public AdminController(
            DailyLoanJobs dailyJobs,
            AccrueInterestService interest,
            PenalAccrualService penal,
            DpdService dpd,
            DueSoonService dueSoon,
            BusinessCalendar calendar) {
        this.dailyJobs = Objects.requireNonNull(dailyJobs);
        this.interest = Objects.requireNonNull(interest);
        this.penal = Objects.requireNonNull(penal);
        this.dpd = Objects.requireNonNull(dpd);
        this.dueSoon = Objects.requireNonNull(dueSoon);
        this.calendar = Objects.requireNonNull(calendar);
    }

    /** Run a named scheduled job for a business date (defaults to the current business date). */
    @PostMapping("/jobs/{name}/run")
    public Map<String, Object> runJob(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate businessDate = date != null ? date : calendar.today();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job", name);
        result.put("date", businessDate.toString());
        switch (name) {
            case "daily" -> {
                DailyLoanJobs.Result r = dailyJobs.runFor(businessDate);
                result.put("interest", r.interest());
                result.put("penal", r.penal());
                result.put("dpd", r.dpd());
                result.put("dueSoon", r.dueSoon());
            }
            case "interest-accrual" -> result.put("recorded", interest.runFor(businessDate));
            case "penal-accrual" -> result.put("recorded", penal.runFor(businessDate));
            case "dpd" -> result.put("recorded", dpd.runFor(businessDate));
            case "due-soon" -> result.put("recorded", dueSoon.runFor(businessDate));
            default -> throw new IllegalArgumentException("unknown job: " + name);
        }
        return result;
    }
}
