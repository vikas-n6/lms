package com.lms.repayment.adapters.in.jobs;

import com.lms.repayment.application.AutoDebitService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the auto-debit job for a business date (phase prompt task 3). Driven by the business
 * calendar in the running stack; exposed as an endpoint so the e2e script can trigger it
 * deterministically.
 */
@RestController
public class AutoDebitJobController {

    private final AutoDebitService autoDebitService;

    public AutoDebitJobController(AutoDebitService autoDebitService) {
        this.autoDebitService = autoDebitService;
    }

    @PostMapping("/admin/jobs/auto-debit/run")
    public Map<String, Object> run(@RequestParam("date") String date) {
        int requested = autoDebitService.run(LocalDate.parse(date));
        return Map.of("businessDate", date, "debitsRequested", requested);
    }
}
