package com.lms.customer.domain;

import com.lms.events.Uuid7;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** A customer aggregate id (UUIDv7, time-ordered; CLAUDE.md "IDs"). */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId newId(Clock clock) {
        return new CustomerId(Uuid7.generate(clock));
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
