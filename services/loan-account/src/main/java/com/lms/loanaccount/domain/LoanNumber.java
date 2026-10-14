package com.lms.loanaccount.domain;

/**
 * Human-facing loan number formatting (phase prompt task 2): {@code LN-<year>-<6-digit sequence>},
 * e.g. {@code LN-2026-000001}. The sequence comes from a database sequence; this only formats it.
 * Kept separate from the UUIDv7 aggregate id (CLAUDE.md "IDs").
 */
public final class LoanNumber {

    private LoanNumber() {}

    public static String format(int year, long sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        return String.format("LN-%04d-%06d", year, sequence);
    }
}
