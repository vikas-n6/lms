package com.lms.security.pii;

/**
 * Masking helpers for personally identifiable data. PII (phone, PAN-like ids, account numbers,
 * email) must never be logged or stored in plaintext (CLAUDE.md "Logging"). Use these when a value
 * must appear in a log or error at all.
 */
public final class Pii {

    private static final char MASK = '*';

    private Pii() {}

    /** Keep the last {@code visible} characters; mask the rest. Null-safe. */
    public static String maskKeepingLast(String value, int visible) {
        if (value == null) {
            return null;
        }
        if (visible < 0) {
            throw new IllegalArgumentException("visible must be >= 0");
        }
        int keep = Math.min(visible, value.length());
        int masked = value.length() - keep;
        return String.valueOf(MASK).repeat(masked) + value.substring(masked);
    }

    /** Phone numbers: reveal only the last two digits. */
    public static String maskPhone(String phone) {
        return maskKeepingLast(phone, 2);
    }

    /** Account numbers: reveal only the last four characters. */
    public static String maskAccount(String account) {
        return maskKeepingLast(account, 4);
    }

    /** PAN-like ids: reveal only the last four characters. */
    public static String maskPan(String pan) {
        return maskKeepingLast(pan, 4);
    }

    /** Email: reveal the first character of the local part and the full domain. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return maskKeepingLast(email, 0);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal =
                local.charAt(0) + String.valueOf(MASK).repeat(Math.max(0, local.length() - 1));
        return maskedLocal + domain;
    }
}
