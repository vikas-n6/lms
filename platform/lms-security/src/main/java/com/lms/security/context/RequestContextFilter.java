package com.lms.security.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds a {@link RequestContext} as a {@link ScopedValue} for the duration of each request, so
 * logging and downstream code can read the trace id, user and idempotency key without ThreadLocal.
 */
public final class RequestContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RequestContext context =
                RequestContext.empty()
                        .withTraceId(header(request, TRACE_ID_HEADER))
                        .withUserId(
                                request.getUserPrincipal() == null
                                        ? null
                                        : request.getUserPrincipal().getName())
                        .withIdempotencyKey(header(request, IDEMPOTENCY_KEY_HEADER));

        try {
            RequestContextHolder.runWhere(
                    context,
                    () -> {
                        chain.doFilter(request, response);
                        return null;
                    });
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return (value == null || value.isBlank()) ? null : value;
    }
}
