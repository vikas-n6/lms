package com.lms.decision.domain;

import com.lms.events.Uuid7;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** A decision aggregate id (UUIDv7, time-ordered; CLAUDE.md "IDs"). */
public record DecisionId(UUID value) {

    public DecisionId {
        Objects.requireNonNull(value, "value");
    }

    public static DecisionId newId(Clock clock) {
        return new DecisionId(Uuid7.generate(clock));
    }

    public static DecisionId of(String value) {
        return new DecisionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
