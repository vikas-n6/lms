package com.lms.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * A percentage rate (for example 24 meaning 24%). Stored as a percentage, not a fraction.
 *
 * <p>Rate is the only sanctioned multiplier for {@link Money}; see {@link Money#percentOf}. All
 * {@link BigDecimal} usage is confined to {@code lms-money} (see docs/domain-rules.md "Money
 * type").
 */
public record Rate(BigDecimal percentage) {

    public Rate {
        Objects.requireNonNull(percentage, "percentage");
    }

    /** A rate from a decimal string expressed in percent, e.g. {@code "2.5"} for 2.5%. */
    public static Rate percent(String percentage) {
        return new Rate(new BigDecimal(percentage));
    }

    /** A rate from a whole-number percent, e.g. {@code percent(24)} for 24%. */
    public static Rate percent(long percentage) {
        return new Rate(BigDecimal.valueOf(percentage));
    }

    public static Rate zero() {
        return new Rate(BigDecimal.ZERO);
    }

    /** The percentage as a plain decimal string (e.g. {@code "28.1234"}); keeps BigDecimal here. */
    public String asPlainString() {
        return percentage.toPlainString();
    }

    /**
     * Divide the rate (e.g. an APR) by a whole divisor, using DECIMAL128 for non-terminating
     * results.
     */
    public Rate dividedBy(int divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("divisor must be non-zero");
        }
        return new Rate(percentage.divide(BigDecimal.valueOf(divisor), MathContext.DECIMAL128));
    }

    /**
     * The rate as a fraction (percentage / 100). Package-private: only {@link Money} consumes it.
     */
    BigDecimal asFraction() {
        return percentage.movePointLeft(2);
    }
}
