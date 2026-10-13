package com.lms.loanaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Loan-account service: owns loan contracts, the EMI schedule, interest/penal accrual, DPD, the
 * repayment allocation waterfall, the loan state machine and foreclosure (docs/architecture.md).
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
public class LoanAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanAccountApplication.class, args);
    }
}
