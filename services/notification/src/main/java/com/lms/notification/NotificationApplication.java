package com.lms.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification stub (phase prompt S7): consume application.* / customer.* and log via the SMS
 * simulator. The full service (templates, delivery tracking) arrives in Phase 4.
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
