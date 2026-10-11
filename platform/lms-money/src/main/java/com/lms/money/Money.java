package com.lms.money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact monetary amount, stored as {@code long} minor units of a specific {@link Currency}.
 *
 * <p>The minor-unit scale is ISO 4217 driven ({@link Currency#getDefaultFractionDigits()}): 2 for
 * INR, 0 for JPY, 3 for KWD. Nothing in this type hard-codes a scale. All arithmetic is
 * overflow-checked and all rounding lives here (docs/domain-rules.md "Money type"). This is the
 * only module permitted to use {@link BigDecimal}.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /**
     * Build an amount from a major-unit decimal string (e.g. {@code "50000.00"} for INR). The scale
     * must not exceed the currency's fraction digits, otherwise minor units would be lost.
     */
    public static Money ofMajor(String major, Currency currency) {
        Objects.requireNonNull(major, "major");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        BigDecimal scaled = new BigDecimal(major).movePointRight(digits);
        if (scaled.remainder(BigDecimal.ONE).signum() != 0) {
            throw new IllegalArgumentException(
                    "amount "
                            + major
                            + " has more precision than "
                            + currency.getCurrencyCode()
                            + " allows ("
                            + digits
                            + " fraction digits)");
        }
        return new Money(scaled.longValueExact(), currency);
    }

    /** The amount as a major-unit decimal (e.g. 500000 paise -> {@code "5000.00"} for INR). */
    public String toMajorString() {
        return BigDecimal.valueOf(minorUnits)
                .movePointLeft(currency.getDefaultFractionDigits())
                .toPlainString();
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    /** Multiply by a whole factor (e.g. a 3x income cap), overflow-checked. */
    public Money times(long factor) {
        return new Money(Math.multiplyExact(minorUnits, factor), currency);
    }

    /** The smaller of this and {@code other} (same currency). */
    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    /** The larger of this and {@code other} (same currency). */
    public Money max(Money other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public Money abs() {
        return minorUnits < 0 ? negate() : this;
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    /** Multiply by a {@link Rate}, rounding to whole minor units with the given mode. */
    public Money percentOf(Rate rate, RoundingMode rounding) {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(rounding, "rounding");
        long result =
                BigDecimal.valueOf(minorUnits)
                        .multiply(rate.asFraction())
                        .setScale(0, rounding)
                        .longValueExact();
        return new Money(result, currency);
    }

    /** Multiply by a {@link Rate}, rounding HALF_UP (the default per docs/domain-rules.md). */
    public Money percentOf(Rate rate) {
        return percentOf(rate, RoundingMode.HALF_UP);
    }

    /**
     * Split this amount across the given weights using the largest-remainder method, so the parts
     * always sum back to exactly this amount. Weights must be non-negative with a positive sum.
     */
    public Money[] allocate(long... weights) {
        if (weights.length == 0) {
            throw new IllegalArgumentException("at least one weight is required");
        }
        long total = 0L;
        for (long w : weights) {
            if (w < 0L) {
                throw new IllegalArgumentException("weights must be non-negative");
            }
            total = Math.addExact(total, w);
        }
        if (total == 0L) {
            throw new IllegalArgumentException("sum of weights must be positive");
        }

        int n = weights.length;
        BigInteger amount = BigInteger.valueOf(minorUnits);
        BigInteger totalW = BigInteger.valueOf(total);
        long[] base = new long[n];
        long[] remainder = new long[n];
        BigInteger allocated = BigInteger.ZERO;

        for (int i = 0; i < n; i++) {
            BigInteger numerator = amount.multiply(BigInteger.valueOf(weights[i]));
            BigInteger[] qr = numerator.divideAndRemainder(totalW);
            BigInteger q = qr[0];
            BigInteger r = qr[1];
            // Convert truncation-toward-zero into floor division so the remainder is in [0, total).
            if (r.signum() < 0) {
                q = q.subtract(BigInteger.ONE);
                r = r.add(totalW);
            }
            base[i] = q.longValueExact();
            remainder[i] = r.longValueExact();
            allocated = allocated.add(q);
        }

        long leftover = amount.subtract(allocated).longValueExact(); // in [0, n)
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // Largest fractional remainder first; ties keep the original order (stable sort on index).
        Arrays.sort(
                order,
                Comparator.<Integer>comparingLong(i -> remainder[i])
                        .reversed()
                        .thenComparingInt(i -> i));
        for (int k = 0; k < leftover; k++) {
            base[order[k]] += 1L;
        }

        Money[] parts = new Money[n];
        for (int i = 0; i < n; i++) {
            parts[i] = new Money(base[i], currency);
        }
        return parts;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + toMajorString();
    }
}
