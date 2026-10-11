package com.lms.observability.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.lms.security.context.RequestContext;
import com.lms.security.context.RequestContextHolder;

/**
 * A Logback converter that emits a field from the current {@link RequestContext} (carried as a
 * ScopedValue, not MDC/ThreadLocal). Configure with the field name as the option, e.g. {@code
 * %requestContext{traceId}}. Every log line carries {@code traceId} (CLAUDE.md "Logging").
 */
public final class RequestContextConverter extends ClassicConverter {

    private String field = "traceId";

    @Override
    public void start() {
        String option = getFirstOption();
        if (option != null && !option.isBlank()) {
            field = option;
        }
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        RequestContext context = RequestContextHolder.current();
        String value =
                switch (field) {
                    case "userId" -> context.userId();
                    case "tenantId" -> context.tenantId();
                    case "idempotencyKey" -> context.idempotencyKey();
                    default -> context.traceId();
                };
        return value == null ? "-" : value;
    }
}
