package com.lms.simulators.mandate;

/** A mandate recorded by the mandate simulator. */
public record Mandate(String mandateId, String loanId, String customerId, String status) {

    public static final String ACTIVE = "ACTIVE";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public Mandate cancelled() {
        return new Mandate(mandateId, loanId, customerId, CANCELLED);
    }
}
