package com.lms.origination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private Application draft() {
        return Application.draft(
                ApplicationId.of("018f9a00-0000-7000-8000-000000000001"),
                "cust-1",
                "MICRO-V1",
                Money.of(5_000_000L, INR),
                "9000000001",
                LocalDate.of(1992, 1, 1),
                Money.of(5_000_000L, INR),
                "SALARIED",
                true,
                true,
                true,
                NOW);
    }

    @Test
    void movesThroughTheHappyPath() {
        Application app = draft().submit(NOW).recordDecision(NOW);
        assertThat(app.status()).isInstanceOf(ApplicationStatus.Decisioned.class);
        app = app.reject(NOW);
        assertThat(app.status()).isInstanceOf(ApplicationStatus.Rejected.class);
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> draft().accept(null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatedAtAdvancesOnTransition() {
        Instant later = NOW.plusSeconds(60);
        Application submitted = draft().submit(later);
        assertThat(submitted.updatedAt()).isEqualTo(later);
        assertThat(submitted.createdAt()).isEqualTo(NOW);
    }
}
