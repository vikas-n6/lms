package com.lms.security.context;

/**
 * Per-request context propagated as a {@link java.lang.ScopedValue} (CLAUDE.md: ThreadLocal is
 * banned). Any field may be null when not yet known. Carries the trace id, authenticated user,
 * tenant and idempotency key so logging and downstream code can read them without passing them
 * through every method signature.
 */
public record RequestContext(
        String traceId, String userId, String tenantId, String idempotencyKey) {

    public static RequestContext empty() {
        return new RequestContext(null, null, null, null);
    }

    public RequestContext withTraceId(String newTraceId) {
        return new RequestContext(newTraceId, userId, tenantId, idempotencyKey);
    }

    public RequestContext withUserId(String newUserId) {
        return new RequestContext(traceId, newUserId, tenantId, idempotencyKey);
    }

    public RequestContext withTenantId(String newTenantId) {
        return new RequestContext(traceId, userId, newTenantId, idempotencyKey);
    }

    public RequestContext withIdempotencyKey(String newIdempotencyKey) {
        return new RequestContext(traceId, userId, tenantId, newIdempotencyKey);
    }
}
