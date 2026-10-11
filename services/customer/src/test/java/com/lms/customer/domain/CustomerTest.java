package com.lms.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void registerStartsInRegisteredStateWithClockTimestamp() {
        Customer customer =
                Customer.register(clock, "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1));
        assertThat(customer.status()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(customer.createdAt()).isEqualTo(Instant.parse("2026-10-11T10:00:00Z"));
        assertThat(customer.id()).isNotNull();
    }

    @Test
    void displayNameIsTheGivenNameOnly() {
        Customer customer =
                Customer.register(clock, "Asha Rao", "9876543210", LocalDate.of(1990, 1, 1));
        assertThat(customer.displayName()).isEqualTo("Asha");
    }

    @Test
    void displayNameHandlesSingleName() {
        Customer customer =
                Customer.register(clock, "Asha", "9876543210", LocalDate.of(1990, 1, 1));
        assertThat(customer.displayName()).isEqualTo("Asha");
    }

    @Test
    void blankFieldsAreRejected() {
        assertThatThrownBy(
                        () ->
                                Customer.register(
                                        clock, "  ", "9876543210", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Customer.register(clock, "Asha", " ", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
