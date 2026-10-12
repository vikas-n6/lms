package com.lms.customer.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An append-only ledger of a customer's consents (phase prompt S3 / CLAUDE.md "append-only consent
 * records"). {@link #grant} returns a new ledger with the consent appended — nothing is ever
 * updated or removed — so the grant history is auditable. A consent is considered held if it
 * appears at least once.
 */
public record ConsentLedger(List<Consent> entries) {

    public ConsentLedger {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }

    public static ConsentLedger empty() {
        return new ConsentLedger(List.of());
    }

    /** Append a consent grant, returning a new ledger (append-only). */
    public ConsentLedger grant(ConsentType type, Instant at) {
        List<Consent> next = new ArrayList<>(entries);
        next.add(new Consent(type, at));
        return new ConsentLedger(next);
    }

    public boolean hasGranted(ConsentType type) {
        return entries.stream().anyMatch(c -> c.type() == type);
    }

    /** True when every type in {@code required} has been granted at least once. */
    public boolean hasAll(Set<ConsentType> required) {
        for (ConsentType type : required) {
            if (!hasGranted(type)) {
                return false;
            }
        }
        return true;
    }

    /** True when all three Phase 1 consents are present (the submission precondition). */
    public boolean hasAllRequired() {
        return hasAll(EnumSet.allOf(ConsentType.class));
    }
}
