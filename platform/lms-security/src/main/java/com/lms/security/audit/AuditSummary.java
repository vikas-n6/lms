package com.lms.security.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;

/**
 * Builds a deterministic, PII-free {@code "k=v; k=v"} summary string for an {@link AuditEntry}.
 *
 * <p>Summaries describe a change structurally — field names, configuration values, versions, state
 * names — never raw personally identifiable data (CLAUDE.md "Logging"). Any value that could be PII
 * must be passed through {@link #withMasked} with a {@code com.lms.security.pii.Pii} masker, so it
 * is reduced before it reaches the audit store. Rendering preserves insertion order so a replay of
 * the same change produces the same summary.
 */
public final class AuditSummary {

    private final Map<String, String> fields = new LinkedHashMap<>();

    private AuditSummary() {}

    public static AuditSummary create() {
        return new AuditSummary();
    }

    /** Shorthand for a state transition summary: {@code state=FROM->TO}. */
    public static AuditSummary transition(String from, String to) {
        return create().with("state", from + "->" + to);
    }

    public AuditSummary with(String key, Object value) {
        fields.put(requireKey(key), String.valueOf(value));
        return this;
    }

    /** Store {@code masker.apply(sensitive)} — never the raw value. */
    public AuditSummary withMasked(String key, String sensitive, UnaryOperator<String> masker) {
        Objects.requireNonNull(masker, "masker");
        fields.put(requireKey(key), masker.apply(sensitive));
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** The rendered summary, or {@code null} when empty so the event payload field stays null. */
    public String render() {
        if (fields.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("; ");
        fields.forEach((k, v) -> joiner.add(k + "=" + v));
        return joiner.toString();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("summary key must be non-blank");
        }
        return key;
    }
}
