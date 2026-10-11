package com.lms.origination.domain;

/**
 * A trigger that may move an application between {@link ApplicationStatus} values. Guards about
 * eligibility (KYC verified, bank linked, consents present) are enforced in the application layer
 * before {@code Submit} is applied; this type only names the lifecycle triggers.
 */
public sealed interface ApplicationTrigger
        permits ApplicationTrigger.Submit,
                ApplicationTrigger.RecordDecision,
                ApplicationTrigger.MakeOffer,
                ApplicationTrigger.Reject,
                ApplicationTrigger.Accept,
                ApplicationTrigger.Expire,
                ApplicationTrigger.Withdraw {

    /** The customer (or back-office) submits a completed draft. */
    record Submit() implements ApplicationTrigger {}

    /** A decision.completed event is recorded against the application. */
    record RecordDecision() implements ApplicationTrigger {}

    /** An approved (or admin-overridden) decision is turned into an offer. */
    record MakeOffer() implements ApplicationTrigger {}

    /** A rejected (or admin-overridden) decision closes the application. */
    record Reject() implements ApplicationTrigger {}

    /** The customer accepts an offer after an OTP e-sign. */
    record Accept() implements ApplicationTrigger {}

    /** The offer passes its expiry without acceptance. */
    record Expire() implements ApplicationTrigger {}

    /** The customer (or back-office) withdraws before acceptance. */
    record Withdraw() implements ApplicationTrigger {}
}
