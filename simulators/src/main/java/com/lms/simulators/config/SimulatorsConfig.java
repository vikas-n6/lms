package com.lms.simulators.config;

import com.lms.simulators.callback.SimCallbackSigner;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Platform beans for the simulators: a UTC clock, a virtual-thread callback executor, the signer.
 */
@Configuration
public class SimulatorsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Runs asynchronous callbacks (KYC, rail, mandate, payments), one virtual thread per task
     * (never pooled — CLAUDE.md "Java 25 usage rules").
     */
    @Bean
    public Executor callbackExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Signs outbound callbacks. The shared secret comes from the environment with a safe local
     * default (twelve-factor; never a secret in Git).
     */
    @Bean
    public SimCallbackSigner simCallbackSigner(
            @Value("${lms.callback.secret:local-dev-callback-secret}") String secret) {
        return new SimCallbackSigner(secret);
    }
}
