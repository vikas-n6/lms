package com.lms.repayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Repayment service: owns the payment lifecycle — mandates, auto-debit, payment links and manual
 * receipts — recording confirmed funds as {@code repayment.received} and returns as {@code
 * repayment.reversed}. It never writes to or reads the loan database (phase prompt ownership);
 * which instalments are due comes from loan-account through the gateway.
 */
@SpringBootApplication
public class RepaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepaymentApplication.class, args);
    }
}
