# ADR 0009: HMAC-signed asynchronous callbacks

- Status: Accepted
- Date: 2026-10-25

## Context

Phase 3 introduces asynchronous provider callbacks (rail payout result, mandate debit result,
payment-link completion) that drive money movement. `docs/architecture.md` and the phase prompt
require that callbacks bypass JWT at the gateway but are never accepted without a valid signature,
and that duplicates and replays are rejected. The providers are our own simulators, so we own both
the signing and the verification.

## Decision

1. **Scheme.** A callback carries `X-Callback-Signature: t=<epochMillis>,v1=<base64(HMAC-SHA256(secret,
   epochMillis + "." + rawBody))>`. The signature covers a timestamp and the exact raw request body,
   so tampering with either fails verification. The shared secret comes from `lms.callback.secret`
   (environment, safe local default), never from Git (CLAUDE.md).
2. **Where it lives.** `CallbackSigner` and `CallbackVerifier` are pure JDK-crypto classes in
   `lms-security` (`javax.crypto.Mac`, no new dependency). The verifying services (disbursement,
   repayment) depend on `lms-security`; the simulators sign with a format-identical `SimCallbackSigner`
   and stay free of Spring Security on their runtime classpath (a cross-verification test pins the two
   implementations together so they cannot drift).
3. **Verification.** `CallbackVerifier.verify` recomputes the HMAC with a constant-time compare
   (`MessageDigest.isEqual`) and rejects any signature whose timestamp is outside a configurable skew
   window (`lms.callback.tolerance-seconds`, default 300), bounding replay. A malformed header is a
   rejection, never an exception, mapped to HTTP 401.
4. **Raw-body integrity through the gateway.** Callback routes (`/callbacks/**`) are routed straight
   through with no `/api` rewrite, so the body the service verifies is byte-for-byte what the
   simulator signed.
5. **De-duplication.** On top of the signature, each callback is deduped on `(providerReference,
   status)` (a Redis fast-path plus a Postgres unique key as the final guard), so duplicate and
   out-of-order deliveries are acknowledged with 200 and processed once.

## Consequences

- One signing format across signer and verifier, enforced by a test; the secret is configuration.
- Timestamp skew and clock come from the injected `Clock`, so verification is deterministically
  testable (done in `CallbackSigningTest`, `SimCallbackSignerTest`, and the services' callback tests).
- Live over-the-wire delivery (simulator → service) is exercised in the Docker session; the signing,
  verification, skew and dedup logic are fully unit-tested now.
