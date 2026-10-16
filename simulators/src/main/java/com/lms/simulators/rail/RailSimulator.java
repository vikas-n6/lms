package com.lms.simulators.rail;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.callback.SignedCallbackClient;
import com.lms.simulators.config.SimConfigRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;

/**
 * Payment-rail simulator (phase prompt S3): an idempotent payout endpoint plus a status query and a
 * signed asynchronous callback. The outcome is deterministic in the idempotency key (a {@code
 * -FAIL} suffix fails), so scenarios repeat. One payout is stored per key; a repeat POST returns
 * the same record and schedules no second callback, and a lost response ({@code
 * unknownOutcomeRate}) still leaves the payout queryable by key — never a blind second payout.
 */
@Component
public class RailSimulator {

    static final String SIM = "rail";

    private final SimBehavior behavior;
    private final SimConfigRegistry registry;
    private final Executor callbackExecutor;
    private final SignedCallbackClient callbackClient;
    private final Map<String, Payout> payouts = new ConcurrentHashMap<>();

    public RailSimulator(
            SimBehavior behavior,
            SimConfigRegistry registry,
            Executor callbackExecutor,
            SignedCallbackClient callbackClient) {
        this.behavior = behavior;
        this.registry = registry;
        this.callbackExecutor = callbackExecutor;
        this.callbackClient = callbackClient;
    }

    /**
     * Initiate (or replay) a payout. Idempotent on {@code idempotencyKey}: the first call records
     * the payout and schedules its callback; a repeat returns the same record with no new callback.
     * A transport failure from {@link SimBehavior#gate} records nothing, so the caller may retry.
     */
    public Payout payout(PayoutRequest request) {
        String key = request.idempotencyKey();
        Payout existing = payouts.get(key);
        if (existing != null) {
            return existing;
        }
        behavior.gate(SIM); // may throw a transport failure before anything is recorded

        Payout decided = decide(request);
        Payout stored = payouts.putIfAbsent(key, decided);
        if (stored != null) {
            return stored; // lost a race; the winner's payout stands (still exactly one)
        }
        scheduleCallback(decided, request.callbackUrl());

        // Unknown outcome: the payout is recorded and callback scheduled, but the HTTP response is
        // lost. The caller must query status by key, not issue a second payout.
        if (behavior.shouldLoseResponse(SIM)) {
            throw new ResponseLostException(key);
        }
        return decided;
    }

    public Optional<Payout> byKey(String idempotencyKey) {
        return Optional.ofNullable(payouts.get(idempotencyKey));
    }

    static Payout decide(PayoutRequest request) {
        String key = request.idempotencyKey();
        boolean fail = key != null && key.toUpperCase(java.util.Locale.ROOT).contains("FAIL");
        String railRef = "UTR-" + Integer.toHexString(Math.abs(String.valueOf(key).hashCode()));
        return new Payout(
                key,
                railRef,
                request.loanId(),
                request.amountMinor(),
                request.currency(),
                fail ? Payout.FAILED : Payout.SUCCESS,
                fail ? "RAIL_DECLINED" : null);
    }

    private void scheduleCallback(Payout payout, String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        long delayMs = registry.get(SIM).callbackDelayMs();
        boolean duplicate = behavior.shouldDuplicateCallback(SIM);
        callbackExecutor.execute(
                () -> {
                    sleep(delayMs);
                    RailCallback body =
                            new RailCallback(
                                    payout.railRef(),
                                    payout.idempotencyKey(),
                                    payout.loanId(),
                                    payout.outcome(),
                                    payout.succeeded() ? payout.railRef() : null,
                                    payout.failureCode());
                    callbackClient.deliver(callbackUrl, body);
                    if (duplicate) {
                        callbackClient.deliver(callbackUrl, body);
                    }
                });
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted before rail callback", e);
        }
    }

    /** Request body for a payout. */
    public record PayoutRequest(
            String idempotencyKey,
            String loanId,
            long amountMinor,
            String currency,
            String callbackUrl) {}

    /** Signed callback body the rail posts back asynchronously. */
    public record RailCallback(
            String railRef,
            String idempotencyKey,
            String loanId,
            String status,
            String utr,
            String failureCode) {}

    /** Raised when the payout is recorded but its HTTP response is lost (unknown outcome). */
    public static final class ResponseLostException extends RuntimeException {
        public ResponseLostException(String idempotencyKey) {
            super("payout response lost for key " + idempotencyKey + "; query status by key");
        }
    }
}
