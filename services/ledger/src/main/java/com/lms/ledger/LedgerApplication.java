package com.lms.ledger;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The ledger service: double-entry ledger, posting templates, trial balance and reconciliation
 * (docs/architecture.md "ledger"). Owns {@code accounts}, {@code journal_entries}, {@code postings}
 * and {@code recon_runs}.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@Import(ResourceServerSecurityConfig.class)
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
