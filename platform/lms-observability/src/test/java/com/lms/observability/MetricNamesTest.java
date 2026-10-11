package com.lms.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricNamesTest {

    @Test
    void metricNamesAreNamespacedAndDotDelimited() {
        assertThat(MetricNames.OUTBOX_PUBLISHED).isEqualTo("lms.outbox.published");
        assertThat(MetricNames.OUTBOX_DEAD_LETTERED).startsWith("lms.").contains(".");
        assertThat(MetricNames.INBOX_DUPLICATE_SKIPPED).startsWith("lms.");
        assertThat(MetricNames.IDEMPOTENCY_OUTCOME).startsWith("lms.");
    }
}
