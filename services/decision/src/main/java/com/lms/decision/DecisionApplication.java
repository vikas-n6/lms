package com.lms.decision;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The decision service: eligibility rules, scorecard, bureau fan-out and explainable decisions
 * (docs/architecture.md "decision"). Owns {@code rule_sets}, {@code decisions} and {@code
 * bureau_snapshots}.
 */
@SpringBootApplication
@Import(ResourceServerSecurityConfig.class)
public class DecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionApplication.class, args);
    }
}
