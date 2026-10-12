package com.lms.origination.domain;

import com.lms.events.Uuid7;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** An application aggregate id (UUIDv7, time-ordered; CLAUDE.md "IDs"). */
public record ApplicationId(UUID value) {

    public ApplicationId {
        Objects.requireNonNull(value, "value");
    }

    public static ApplicationId newId(Clock clock) {
        return new ApplicationId(Uuid7.generate(clock));
    }

    public static ApplicationId of(String value) {
        return new ApplicationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
