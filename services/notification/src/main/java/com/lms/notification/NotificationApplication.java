package com.lms.notification;

import com.lms.security.jwt.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification service (phase prompt task 5): versioned templates with placeholders over SMS,
 * email, push and WhatsApp simulators; dedup on (eventId, templateId, channel); retry with backoff;
 * consent and a quiet-hours window with morning release. Subscribes to the application,
 * disbursement, loan, repayment and collections events and publishes {@code
 * notification.sent|failed}.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@Import(ResourceServerSecurityConfig.class)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
