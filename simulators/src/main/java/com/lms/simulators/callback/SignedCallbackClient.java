package com.lms.simulators.callback;

/**
 * Delivers a signed asynchronous callback to a peer URL. Implemented over HTTP in the running stack
 * ({@link HttpSignedCallbackClient}); a fake is substituted in tests so the simulators' decision
 * logic is unit-testable without a peer.
 */
public interface SignedCallbackClient {

    /**
     * POST {@code body} (as JSON) to {@code callbackUrl} with a valid callback signature header.
     */
    void deliver(String callbackUrl, Object body);
}
