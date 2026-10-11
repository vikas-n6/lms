package com.lms.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.security.context.RequestContext;
import com.lms.security.context.RequestContextHolder;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestContextConverterTest {

    @Test
    void emitsTraceIdWithinScope() throws Throwable {
        RequestContextConverter converter = new RequestContextConverter();
        converter.start();
        RequestContextHolder.runWhere(
                RequestContext.empty().withTraceId("trace-1"),
                () -> {
                    assertThat(converter.convert(null)).isEqualTo("trace-1");
                    return null;
                });
    }

    @Test
    void emitsDashWhenUnbound() {
        RequestContextConverter converter = new RequestContextConverter();
        converter.start();
        assertThat(converter.convert(null)).isEqualTo("-");
    }

    @Test
    void selectsTheConfiguredField() throws Throwable {
        RequestContextConverter converter = new RequestContextConverter();
        converter.setOptionList(List.of("userId"));
        converter.start();
        RequestContextHolder.runWhere(
                RequestContext.empty().withUserId("alice"),
                () -> {
                    assertThat(converter.convert(null)).isEqualTo("alice");
                    return null;
                });
    }
}
