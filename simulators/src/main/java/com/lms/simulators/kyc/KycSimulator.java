package com.lms.simulators.kyc;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;

/**
 * KYC simulator (phase prompt S2). Deterministic in the subject id: a {@code -FAIL} suffix yields
 * FAILED, {@code -PENDING} yields PENDING (resolved to VERIFIED via an asynchronous callback after
 * {@code callbackDelayMs}, duplicated per {@code duplicateCallbackRate}), everything else VERIFIED.
 */
@Component
public class KycSimulator {

    private final SimBehavior behavior;
    private final SimConfigRegistry registry;
    private final Executor callbackExecutor;
    private final KycCallbackClient callbackClient;

    public KycSimulator(
            SimBehavior behavior,
            SimConfigRegistry registry,
            Executor callbackExecutor,
            KycCallbackClient callbackClient) {
        this.behavior = behavior;
        this.registry = registry;
        this.callbackExecutor = callbackExecutor;
        this.callbackClient = callbackClient;
    }

    public KycStatus verify(String subjectId, String callbackUrl) {
        behavior.gate("kyc");
        KycStatus status = decide(subjectId);
        if (status == KycStatus.PENDING && callbackUrl != null && !callbackUrl.isBlank()) {
            long delayMs = registry.get("kyc").callbackDelayMs();
            boolean duplicate = behavior.shouldDuplicateCallback("kyc");
            callbackExecutor.execute(() -> deliver(callbackUrl, subjectId, delayMs, duplicate));
        }
        return status;
    }

    static KycStatus decide(String subjectId) {
        String id = String.valueOf(subjectId);
        if (id.endsWith("-FAIL")) {
            return KycStatus.FAILED;
        }
        if (id.endsWith("-PENDING")) {
            return KycStatus.PENDING;
        }
        return KycStatus.VERIFIED;
    }

    private void deliver(String callbackUrl, String subjectId, long delayMs, boolean duplicate) {
        sleep(delayMs);
        callbackClient.sendCallback(callbackUrl, subjectId, KycStatus.VERIFIED);
        if (duplicate) {
            callbackClient.sendCallback(callbackUrl, subjectId, KycStatus.VERIFIED);
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
            throw new IllegalStateException("interrupted before KYC callback", e);
        }
    }
}
