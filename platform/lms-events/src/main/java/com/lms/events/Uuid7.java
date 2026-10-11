package com.lms.events;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Generates UUIDv7 identifiers (RFC 9562): a 48-bit big-endian Unix millisecond timestamp followed
 * by the version, variant and random bits. Being time-ordered, these sort by creation time, which
 * suits aggregate ids and event ids (CLAUDE.md "IDs"). Time comes from the injected {@link Clock},
 * never the wall clock.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {}

    public static UUID generate(Clock clock) {
        return generate(clock.instant());
    }

    public static UUID generate(Instant instant) {
        long timestampMillis = instant.toEpochMilli();
        byte[] bytes = new byte[16];
        // 48-bit timestamp, big-endian, in the first six bytes.
        bytes[0] = (byte) (timestampMillis >>> 40);
        bytes[1] = (byte) (timestampMillis >>> 32);
        bytes[2] = (byte) (timestampMillis >>> 24);
        bytes[3] = (byte) (timestampMillis >>> 16);
        bytes[4] = (byte) (timestampMillis >>> 8);
        bytes[5] = (byte) timestampMillis;

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, 10);

        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70); // version 7 in the high nibble
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // variant 10xx

        long mostSignificant = 0;
        long leastSignificant = 0;
        for (int i = 0; i < 8; i++) {
            mostSignificant = (mostSignificant << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            leastSignificant = (leastSignificant << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(mostSignificant, leastSignificant);
    }
}
