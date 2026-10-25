package com.lms.simulators.mandate;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.callback.SignedCallbackClient;
import com.lms.simulators.config.SimConfigRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;

/**
 * Mandate (auto-debit) simulator (phase prompt S3): register, debit and cancel, each with signed
 * asynchronous results. Debits are idempotent on their idempotency key, so a duplicate debit never
 * draws twice. A successful debit may later be returned ({@code returnRate}), modelling a bounced
 * direct debit that had already reported success. Outcomes are deterministic in the ids so
 * scenarios repeat.
 */
@Component
public class MandateSimulator {

    static final String SIM = "mandate";

    private final SimBehavior behavior;
    private final SimConfigRegistry registry;
    private final Executor callbackExecutor;
    private final SignedCallbackClient callbackClient;
    private final Map<String, Mandate> mandates = new ConcurrentHashMap<>();
    private final Map<String, DebitResult> debits = new ConcurrentHashMap<>();

    public MandateSimulator(
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
     * Register a mandate for a loan. FAILED when the customer id carries a {@code -NOMANDATE}
     * marker.
     */
    public Mandate register(RegisterRequest request) {
        behavior.gate(SIM);
        String mandateId = "MND-" + hex(request.loanId());
        boolean ok =
                request.customerId() == null
                        || !request.customerId().toUpperCase(Locale.ROOT).contains("NOMANDATE");
        Mandate mandate =
                new Mandate(
                        mandateId,
                        request.loanId(),
                        request.customerId(),
                        ok ? Mandate.ACTIVE : Mandate.FAILED);
        mandates.put(mandateId, mandate);
        return mandate;
    }

    /**
     * Debit a mandate. Idempotent on {@code idempotencyKey}; FAILED when the key carries a {@code
     * -FAIL} marker or the mandate is not active. A successful debit may schedule a later return.
     */
    public DebitResult debit(DebitRequest request) {
        DebitResult existing = debits.get(request.idempotencyKey());
        if (existing != null) {
            return existing;
        }
        behavior.gate(SIM);
        Mandate mandate = mandates.get(request.mandateId());
        boolean keyFails =
                request.idempotencyKey() != null
                        && request.idempotencyKey().toUpperCase(Locale.ROOT).contains("FAIL");
        boolean ok = mandate != null && mandate.isActive() && !keyFails;
        DebitResult result =
                new DebitResult(
                        request.idempotencyKey(),
                        request.mandateId(),
                        request.loanId(),
                        request.amountMinor(),
                        request.currency(),
                        ok ? DebitResult.SUCCESS : DebitResult.FAILED,
                        ok ? null : (mandate == null ? "NO_MANDATE" : "INSUFFICIENT_FUNDS"));
        DebitResult stored = debits.putIfAbsent(request.idempotencyKey(), result);
        if (stored != null) {
            return stored;
        }
        scheduleDebitCallbacks(result, request.callbackUrl());
        return result;
    }

    public Optional<Mandate> byId(String mandateId) {
        return Optional.ofNullable(mandates.get(mandateId));
    }

    /** Cancel a mandate. Returns false when unknown. */
    public boolean cancel(String mandateId) {
        return mandates.computeIfPresent(mandateId, (id, m) -> m.cancelled()) != null;
    }

    private void scheduleDebitCallbacks(DebitResult result, String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        long delayMs = registry.get(SIM).callbackDelayMs();
        boolean willReturn = result.succeeded() && behavior.shouldReturn(SIM);
        callbackExecutor.execute(
                () -> {
                    sleep(delayMs);
                    callbackClient.deliver(callbackUrl, result.toCallback(result.outcome()));
                    if (willReturn) {
                        sleep(delayMs);
                        callbackClient.deliver(
                                callbackUrl, result.toCallback(DebitResult.RETURNED));
                    }
                });
    }

    private static String hex(Object value) {
        return Integer.toHexString(Math.abs(String.valueOf(value).hashCode()));
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted before mandate callback", e);
        }
    }

    public record RegisterRequest(String loanId, String customerId, String callbackUrl) {}

    public record DebitRequest(
            String idempotencyKey,
            String mandateId,
            String loanId,
            long amountMinor,
            String currency,
            String callbackUrl) {}

    /** Idempotent debit record. */
    public record DebitResult(
            String idempotencyKey,
            String mandateId,
            String loanId,
            long amountMinor,
            String currency,
            String outcome,
            String failureCode) {

        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String RETURNED = "RETURNED";

        public boolean succeeded() {
            return SUCCESS.equals(outcome);
        }

        DebitCallback toCallback(String status) {
            return new DebitCallback(
                    idempotencyKey, mandateId, loanId, amountMinor, currency, status, failureCode);
        }
    }

    /** Signed callback body the mandate rail posts back asynchronously. */
    public record DebitCallback(
            String idempotencyKey,
            String mandateId,
            String loanId,
            long amountMinor,
            String currency,
            String status,
            String failureCode) {}
}
