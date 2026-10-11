package com.lms.security.context;

import java.util.Optional;

/**
 * Holds the current {@link RequestContext} in a {@link ScopedValue}. ScopedValue is final in Java
 * 25 and replaces ThreadLocal (banned): it is immutable, inherited by structured-concurrency
 * subtasks, and automatically unbound when the binding scope ends.
 */
public final class RequestContextHolder {

    private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

    private RequestContextHolder() {}

    /** The bound context, or {@link RequestContext#empty()} when nothing is bound. */
    public static RequestContext current() {
        return CONTEXT.orElse(RequestContext.empty());
    }

    public static Optional<RequestContext> currentIfBound() {
        return CONTEXT.isBound() ? Optional.of(CONTEXT.get()) : Optional.empty();
    }

    public static boolean isBound() {
        return CONTEXT.isBound();
    }

    /** Run {@code op} with {@code context} bound; the binding is removed when it returns. */
    public static <X extends Throwable> void runWhere(
            RequestContext context, ScopedValue.CallableOp<Void, X> op) throws X {
        ScopedValue.where(CONTEXT, context).call(op);
    }
}
