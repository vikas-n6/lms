package com.lms.origination;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The origination service: application lifecycle, offer creation and acceptance
 * (docs/architecture.md "origination"). Owns {@code applications}, {@code application_events} and
 * {@code offers}.
 */
@SpringBootApplication
@Import(ResourceServerSecurityConfig.class)
public class OriginationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OriginationApplication.class, args);
    }
}
