package com.lms.security.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A set of versioned {@link FieldCipher} keys supporting column-encryption key rotation (Phase 5
 * security pass). A stored token is prefixed with its key id ({@code <keyId>:<base64(iv||ct)>}),
 * so:
 *
 * <ul>
 *   <li>{@link #encrypt} always writes with the current key;
 *   <li>{@link #decrypt} reads a token with whichever key wrote it (old keys stay in the ring until
 *       every row is re-encrypted), so rotation needs no downtime;
 *   <li>{@link #rotate} re-encrypts a token under the current key (a no-op if it already is), which
 *       the rotation job calls row by row.
 * </ul>
 *
 * Keys come from the environment (never Git, CLAUDE.md); the rotation procedure is in the runbook.
 */
public final class KeyRing {

    private static final char SEPARATOR = ':';

    private final Map<String, FieldCipher> ciphers;
    private final String currentKeyId;

    public KeyRing(Map<String, FieldCipher> ciphersByKeyId, String currentKeyId) {
        Objects.requireNonNull(ciphersByKeyId, "ciphersByKeyId");
        this.currentKeyId = Objects.requireNonNull(currentKeyId, "currentKeyId");
        if (!ciphersByKeyId.containsKey(currentKeyId)) {
            throw new IllegalArgumentException(
                    "current key id '" + currentKeyId + "' is not in the ring");
        }
        this.ciphers = Map.copyOf(ciphersByKeyId);
    }

    /** Build from base64-encoded keys keyed by id (as supplied via environment variables). */
    public static KeyRing fromBase64(Map<String, String> base64KeysById, String currentKeyId) {
        Map<String, FieldCipher> ciphers = new LinkedHashMap<>();
        base64KeysById.forEach((id, key) -> ciphers.put(id, FieldCipher.fromBase64Key(key)));
        return new KeyRing(ciphers, currentKeyId);
    }

    public String currentKeyId() {
        return currentKeyId;
    }

    public String encrypt(String plaintext) {
        return currentKeyId + SEPARATOR + cipher(currentKeyId).encrypt(plaintext);
    }

    public String decrypt(String token) {
        int sep = token.indexOf(SEPARATOR);
        if (sep <= 0) {
            throw new IllegalArgumentException("token is missing its key-id prefix");
        }
        String keyId = token.substring(0, sep);
        return cipher(keyId).decrypt(token.substring(sep + 1));
    }

    /** True when the token was written under the current key (no rotation needed). */
    public boolean isCurrent(String token) {
        int sep = token.indexOf(SEPARATOR);
        return sep > 0 && currentKeyId.equals(token.substring(0, sep));
    }

    /** Re-encrypt under the current key; returns the token unchanged when already current. */
    public String rotate(String token) {
        if (isCurrent(token)) {
            return token;
        }
        return encrypt(decrypt(token));
    }

    private FieldCipher cipher(String keyId) {
        FieldCipher cipher = ciphers.get(keyId);
        if (cipher == null) {
            throw new IllegalArgumentException("unknown encryption key id '" + keyId + "'");
        }
        return cipher;
    }
}
