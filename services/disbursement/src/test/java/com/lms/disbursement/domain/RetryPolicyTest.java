package com.lms.disbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(2), 2.0);

    @Test
    void retriesUpToMaxAttempts() {
        assertThat(policy.shouldRetry(0)).isTrue();
        assertThat(policy.shouldRetry(1)).isTrue();
        assertThat(policy.shouldRetry(2)).isTrue();
        assertThat(policy.shouldRetry(3)).isFalse(); // all 3 attempts used
    }

    @Test
    void backoffIsExponentialFromTheSecondAttempt() {
        assertThat(policy.backoffBefore(1)).isEqualTo(Duration.ZERO);
        assertThat(policy.backoffBefore(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.backoffBefore(3)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), 2.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ZERO, 2.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(1), 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
