package com.lms.simulators.payments;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.callback.SignedCallbackClient;
import com.lms.simulators.config.SimConfigRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Component;

/**
 * Payment-link simulator (phase prompt S3): create a link and, when auto-completion is requested,
 * post a signed callback confirming funds after the configured delay. Link ids are deterministic in
 * the loan id so scenarios repeat.
 */
@Component
public class PaymentsSimulator {

    static final String SIM = "payments";

    private final SimBehavior behavior;
    private final SimConfigRegistry registry;
    private final Executor callbackExecutor;
    private final SignedCallbackClient callbackClient;
    private final Map<String, PaymentLink> links = new ConcurrentHashMap<>();

    public PaymentsSimulator(
            SimBehavior behavior,
            SimConfigRegistry registry,
            Executor callbackExecutor,
            SignedCallbackClient callbackClient) {
        this.behavior = behavior;
        this.registry = registry;
        this.callbackExecutor = callbackExecutor;
        this.callbackClient = callbackClient;
    }

    public PaymentLink createLink(CreateLinkRequest request) {
        behavior.gate(SIM);
        String linkId =
                "PL-" + Integer.toHexString(Math.abs(String.valueOf(request.loanId()).hashCode()));
        PaymentLink link =
                new PaymentLink(
                        linkId,
                        "https://pay.local/sim/" + linkId,
                        request.loanId(),
                        request.amountMinor(),
                        request.currency(),
                        PaymentLink.CREATED);
        links.put(linkId, link);
        if (request.autoComplete()) {
            scheduleCompletion(link, request.callbackUrl());
        }
        return link;
    }

    public Optional<PaymentLink> byId(String linkId) {
        return Optional.ofNullable(links.get(linkId));
    }

    private void scheduleCompletion(PaymentLink link, String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        long delayMs = registry.get(SIM).callbackDelayMs();
        boolean duplicate = behavior.shouldDuplicateCallback(SIM);
        callbackExecutor.execute(
                () -> {
                    sleep(delayMs);
                    links.computeIfPresent(
                            link.linkId(),
                            (id, l) ->
                                    new PaymentLink(
                                            l.linkId(),
                                            l.url(),
                                            l.loanId(),
                                            l.amountMinor(),
                                            l.currency(),
                                            PaymentLink.COMPLETED));
                    PaymentCallback body =
                            new PaymentCallback(
                                    link.linkId(),
                                    link.loanId(),
                                    link.amountMinor(),
                                    link.currency(),
                                    "SUCCESS");
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
            throw new IllegalStateException("interrupted before payment callback", e);
        }
    }

    public record CreateLinkRequest(
            String loanId,
            long amountMinor,
            String currency,
            boolean autoComplete,
            String callbackUrl) {}

    /** Signed callback body the payments provider posts back asynchronously. */
    public record PaymentCallback(
            String linkId, String loanId, long amountMinor, String currency, String status) {}
}
