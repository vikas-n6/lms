package com.lms.simulators.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimulatorUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SimExceptionHandlerTest {

    private final SimExceptionHandler handler = new SimExceptionHandler();

    @Test
    void mapsUnavailableTo503() {
        assertThat(handler.onUnavailable(new SimulatorUnavailableException("bureau")).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void mapsIllegalArgumentTo400() {
        assertThat(handler.onBadRequest(new IllegalArgumentException("bad")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
