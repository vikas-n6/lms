package com.lms.outbox.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void escalatesThroughRetryTiersThenDlt() {
        assertThat(RetryPolicy.nextTopic("loan.events", 1)).isEqualTo("loan.events.retry.5s");
        assertThat(RetryPolicy.nextTopic("loan.events", 2)).isEqualTo("loan.events.retry.1m");
        assertThat(RetryPolicy.nextTopic("loan.events", 3)).isEqualTo("loan.events.retry.10m");
        assertThat(RetryPolicy.nextTopic("loan.events", 4)).isEqualTo("loan.events.dlt");
        assertThat(RetryPolicy.nextTopic("loan.events", 99)).isEqualTo("loan.events.dlt");
    }

    @Test
    void helpersComposeTopicNames() {
        assertThat(RetryPolicy.retryTopic("customer.events", "retry.5s"))
                .isEqualTo("customer.events.retry.5s");
        assertThat(RetryPolicy.deadLetterTopic("customer.events")).isEqualTo("customer.events.dlt");
    }

    @Test
    void rejectsNonPositiveAttempts() {
        assertThatThrownBy(() -> RetryPolicy.nextTopic("t", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
