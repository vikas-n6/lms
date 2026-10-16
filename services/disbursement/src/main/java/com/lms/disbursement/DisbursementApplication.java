package com.lms.disbursement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Disbursement service: the payout saga runs as a durable Temporal workflow (CLAUDE.md: Temporal is
 * embedded in disbursement only). It consumes {@code loan.created}, drives the rail through a
 * workflow that survives timeouts, duplicate/out-of-order callbacks and crashes, and publishes
 * {@code disbursement.initiated|succeeded|failed}.
 */
@SpringBootApplication
public class DisbursementApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisbursementApplication.class, args);
    }
}
