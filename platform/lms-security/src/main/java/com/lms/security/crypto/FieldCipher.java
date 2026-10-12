package com.lms.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Column-level authenticated encryption for sensitive fields (id number, bank account number) using
 * AES-GCM (CLAUDE.md "no plaintext PII in storage"). The key comes from the environment, never from
 * code or Git. Each call uses a fresh random 96-bit IV; the stored token is {@code base64(iv ||
 * ciphertext||tag)}. Decryption authenticates the tag and fails on any tampering.
 *
 * <p>This is deliberately not a deterministic cipher: equal plaintexts produce different tokens, so
 * it cannot be used for equality lookups. Use {@link BlindIndex} for that.
 */
public final class FieldCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        int len = keyBytes.length;
        if (len != 16 && len != 24 && len != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes, was " + len);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Build from a base64-encoded key (as supplied via an environment variable). */
    public static FieldCipher fromBase64Key(String base64Key) {
        Objects.requireNonNull(base64Key, "base64Key");
        return new FieldCipher(Base64.getDecoder().decode(base64Key));
    }

    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] token = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, token, 0, iv.length);
            System.arraycopy(ciphertext, 0, token, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(token);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("field encryption failed", e);
        }
    }

    public String decrypt(String token) {
        Objects.requireNonNull(token, "token");
        try {
            byte[] bytes = Base64.getDecoder().decode(token);
            if (bytes.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(bytes, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(bytes, IV_BYTES, bytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException e) {
            // Includes AEADBadTagException when the token was tampered with.
            throw new IllegalStateException("field decryption failed (tampered or wrong key)", e);
        }
    }
}
