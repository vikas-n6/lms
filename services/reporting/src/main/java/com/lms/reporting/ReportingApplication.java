package com.lms.reporting;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The reporting service (docs/architecture.md, ADR 0013): denormalised read models built purely by
 * consuming every domain topic, plus the audit store. It owns only its read-model database and
 * reads no other service's data; a rebuild replays every topic from offset 0.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@Import(ResourceServerSecurityConfig.class)
public class ReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingApplication.class, args);
    }
}
