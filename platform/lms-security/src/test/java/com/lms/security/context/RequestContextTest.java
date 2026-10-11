package com.lms.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextTest {

    @Test
    void withersBuildUpTheContextImmutably() {
        RequestContext context =
                RequestContext.empty().withTraceId("t").withUserId("u").withIdempotencyKey("k");
        assertThat(context.traceId()).isEqualTo("t");
        assertThat(context.userId()).isEqualTo("u");
        assertThat(context.idempotencyKey()).isEqualTo("k");
        assertThat(context.tenantId()).isNull();
    }

    @Test
    void holderExposesTheBoundContextOnlyWithinScope() throws Throwable {
        assertThat(RequestContextHolder.isBound()).isFalse();
        assertThat(RequestContextHolder.current()).isEqualTo(RequestContext.empty());

        RequestContext bound = RequestContext.empty().withUserId("alice");
        RequestContextHolder.runWhere(
                bound,
                () -> {
                    assertThat(RequestContextHolder.isBound()).isTrue();
                    assertThat(RequestContextHolder.current().userId()).isEqualTo("alice");
                    assertThat(RequestContextHolder.currentIfBound()).contains(bound);
                    return null;
                });

        assertThat(RequestContextHolder.isBound()).isFalse();
    }

    @Test
    void filterBindsHeadersAndUnbindsAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/customers");
        request.addHeader(RequestContextFilter.TRACE_ID_HEADER, "trace-123");
        request.addHeader(RequestContextFilter.IDEMPOTENCY_KEY_HEADER, "idem-key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<RequestContext> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(RequestContextHolder.current());

        new RequestContextFilter().doFilter(request, response, chain);

        assertThat(seen.get().traceId()).isEqualTo("trace-123");
        assertThat(seen.get().idempotencyKey()).isEqualTo("idem-key-1");
        assertThat(RequestContextHolder.isBound()).isFalse();
    }
}
