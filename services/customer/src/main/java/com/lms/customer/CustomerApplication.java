package com.lms.customer;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The customer service: profile, KYC status, consent and payout-account linking (Phase 0 covers
 * registration only). The reference hexagonal service every later service is copied from.
 */
@SpringBootApplication
@Import(ResourceServerSecurityConfig.class)
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
