# ADR 0016: Versioned column-encryption keys and rotation

- Status: Accepted
- Date: 2026-10-27

## Context

Phase 5 task 7 requires a documented, tested column-encryption key-rotation procedure. The Phase 1
`FieldCipher` (AES-GCM) encrypts PII columns (id number, bank account) with a single key from the
environment, and its token is `base64(iv||ciphertext||tag)` with no key identifier — so a key change
would make existing rows undecryptable. Rotation must be zero-downtime and reversible, and must not
change the cipher or any money path.

## Decision

1. **Versioned keys via a `KeyRing`.** `lms-security` adds `KeyRing`, a set of `FieldCipher`s keyed
   by id with one designated *current* key. Stored tokens become `<keyId>:<base64(iv||ct||tag)>`.
2. **Write-current, read-any.** `encrypt` always uses the current key; `decrypt` selects the key from
   the token's prefix, so rows written under an older key stay readable while old keys remain in the
   ring. This makes a rotation zero-downtime: add the new key as current, keep the old key for reads,
   re-encrypt in the background, then retire the old key.
3. **`rotate` re-encrypts row by row.** `KeyRing.rotate(token)` decrypts with the token's key and
   re-encrypts with the current key, and is a no-op when the token is already current — so the
   rotation job is restartable and idempotent.
4. **Keys are environment configuration.** Keys and the current-key id come from environment
   variables (never Git); the ring is built with `KeyRing.fromBase64`. The operator procedure, and a
   dry-run on a copy of the data, are documented in `docs/runbook.md`.
5. **Additive, cipher unchanged.** `FieldCipher` and the AES-GCM scheme are untouched; `KeyRing` wraps
   it. Existing single-key tokens can be migrated by writing them under a key id on first rotation.
   No money rule, stored amount or schedule changes.

## Consequences

- Rotation is proven in-JVM (`KeyRingTest`: write-current/read-old, rotate-to-current idempotence,
  unknown-key rejection, tamper detection). The live rotation on a data copy is the Docker session's
  to run (backlog).
- The token format changes for newly written values; readers must use `KeyRing`, so a service
  adopting it migrates its read path from `FieldCipher` to `KeyRing` when rotation is enabled.
