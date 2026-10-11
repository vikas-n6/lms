package com.lms.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void rejectsWith429WhenOverBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(key -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean forwarded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> forwarded.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(forwarded).isFalse();
    }

    @Test
    void forwardsWhenWithinBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(key -> true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/customers/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean forwarded = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> forwarded.set(true));

        assertThat(forwarded).isTrue();
    }

    @Test
    void doesNotLimitTheStatusEndpoint() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(key -> false); // would reject if applied
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean forwarded = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> forwarded.set(true));

        assertThat(forwarded).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
