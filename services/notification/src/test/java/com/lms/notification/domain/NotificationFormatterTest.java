package com.lms.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationFormatterTest {

    @Test
    void formatsANonPiiLine() {
        assertThat(NotificationFormatter.line("application.accepted", "application", "app-1"))
                .isEqualTo("notification: application.accepted for application app-1");
    }
}
