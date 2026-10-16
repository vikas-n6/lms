package com.lms.disbursement.adapters.out.temporal;

/**
 * A rail result carried between activities, the callback signal and the workflow. Serialised by
 * Temporal's data converter, so it is a plain record with no behaviour beyond small factories.
 */
public record RailResult(ResultKind kind, String railRef, String utr, String failureCode) {

    public static RailResult pending(String railRef) {
        return new RailResult(ResultKind.PENDING, railRef, null, null);
    }

    public static RailResult success(String railRef, String utr) {
        return new RailResult(ResultKind.SUCCESS, railRef, utr, null);
    }

    public static RailResult failed(String railRef, String failureCode) {
        return new RailResult(ResultKind.FAILED, railRef, null, failureCode);
    }

    public static RailResult unknown() {
        return new RailResult(ResultKind.UNKNOWN, null, null, null);
    }

    // No getter-shaped convenience methods here: Temporal serialises this record with Jackson, and
    // a
    // boolean isX() would add a phantom property that the deserializer then rejects. The terminal
    // check lives in ResultKind.isTerminal(kind) instead.
}
