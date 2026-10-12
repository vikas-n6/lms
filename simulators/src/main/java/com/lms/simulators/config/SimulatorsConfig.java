package com.lms.simulators.config;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Platform beans for the simulators: a UTC clock and a virtual-thread callback executor. */
@Configuration
public class SimulatorsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Runs asynchronous KYC callbacks, one virtual thread per task (never pooled — CLAUDE.md "Java
     * 25 usage rules").
     */
    @Bean
    public Executor kycCallbackExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
