package com.lms.security.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A keyed blind index for equality lookups over encrypted fields (CLAUDE.md "keyed blind index for
 * lookups"). Computes {@code HMAC-SHA256(key, normalized(value))} as lowercase hex: deterministic,
 * so a value can be found by its index, but one-way and keyed, so the index alone does not reveal
 * the value and cannot be built without the key.
 *
 * <p>Normalization strips surrounding whitespace and upper-cases, so lookups are
 * case/whitespace-insensitive (ids and account numbers compared canonically).
 */
public final class BlindIndex {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public BlindIndex(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length < 16) {
            throw new IllegalArgumentException("blind-index key must be at least 16 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public static BlindIndex fromBase64Key(String base64Key) {
        Objects.requireNonNull(base64Key, "base64Key");
        return new BlindIndex(Base64.getDecoder().decode(base64Key));
    }

    public String compute(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.strip().toUpperCase(java.util.Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("blind-index computation failed", e);
        }
    }
}
