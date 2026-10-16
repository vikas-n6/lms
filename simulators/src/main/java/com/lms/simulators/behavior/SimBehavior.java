package com.lms.simulators.behavior;

import com.lms.simulators.config.SimConfigRegistry;
import com.lms.simulators.config.SimulatorConfig;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Applies a simulator's configured latency, failure and timeout to each call (phase prompt S2). All
 * random draws come from the simulator's seeded RNG under a lock, so a given config + seed yields a
 * reproducible sequence of behaviours regardless of real timing.
 */
@Component
public class SimBehavior {

    /** How long a "timeout" stalls before returning, to trip a caller's time limiter. */
    static final long TIMEOUT_STALL_MS = 5_000L;

    private final SimConfigRegistry registry;

    public SimBehavior(SimConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * Gate a simulator call: sleep the configured latency, optionally stall to force a caller
     * timeout, and optionally fail with {@link SimulatorUnavailableException}.
     */
    public void gate(String simulator) {
        SimulatorConfig cfg = registry.get(simulator);
        Random rnd = registry.random(simulator);
        boolean fail;
        boolean timeout;
        int latency;
        synchronized (rnd) {
            fail = rnd.nextDouble() < cfg.failureRate();
            timeout = !fail && rnd.nextDouble() < cfg.timeoutRate();
            int span = cfg.latencyMsMax() - cfg.latencyMsMin();
            latency = cfg.latencyMsMin() + (span > 0 ? rnd.nextInt(span + 1) : 0);
        }
        sleep(latency);
        if (timeout) {
            sleep(TIMEOUT_STALL_MS);
        }
        if (fail) {
            throw new SimulatorUnavailableException(simulator);
        }
    }

    /** Decide (under the seeded RNG) whether a callback should be duplicated. */
    public boolean shouldDuplicateCallback(String simulator) {
        return draw(simulator, registry.get(simulator).duplicateCallbackRate());
    }

    /**
     * Decide whether this operation's response is lost/times out even though it succeeded server
     * side (phase prompt S3 {@code unknownOutcomeRate}). The caller then has to query status by its
     * idempotency key rather than issue a second operation.
     */
    public boolean shouldLoseResponse(String simulator) {
        return draw(simulator, registry.get(simulator).unknownOutcomeRate());
    }

    /**
     * Decide whether asynchronous callbacks for this operation should be delivered out of order.
     */
    public boolean shouldDeliverOutOfOrder(String simulator) {
        return draw(simulator, registry.get(simulator).outOfOrderRate());
    }

    /** Decide whether a succeeded debit is later returned (phase prompt S3 {@code returnRate}). */
    public boolean shouldReturn(String simulator) {
        return draw(simulator, registry.get(simulator).returnRate());
    }

    private boolean draw(String simulator, double probability) {
        Random rnd = registry.random(simulator);
        synchronized (rnd) {
            return rnd.nextDouble() < probability;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while simulating latency", e);
        }
    }
}
