package com.lms.collections;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Collections service: collection cases and durable Temporal dunning workflows (CLAUDE.md: Temporal
 * embedded in disbursement and collections only). It reacts to {@code loan.dpd-updated},
 * repayment/mandate and loan close/write-off events — never reading loan tables — opening cases at
 * DPD 1 or more, advancing dunning on DPD ticks, and publishing {@code collections.*} events.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@Import(ResourceServerSecurityConfig.class)
public class CollectionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectionsApplication.class, args);
    }
}
