package com.lms.disbursement.application;

import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.application.port.out.RailCallbackDedupStore;
import com.lms.disbursement.domain.CallbackResolution;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.DisbursementStateMachine;
import com.lms.disbursement.domain.RailStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Classifies an inbound (already signature-verified) rail callback so the controller can act:
 * signal the workflow (APPLIED), ignore a duplicate, count a conflicting terminal result, or reject
 * an unknown disbursement. De-duplication on {@code (railRef, status)} happens first, then the
 * domain {@link DisbursementStateMachine} rule against the persisted state.
 */
public class CallbackClassifier {

    public enum Result {
        APPLIED,
        DUPLICATE,
        CONFLICT,
        UNKNOWN_DISBURSEMENT
    }

    private final DisbursementRepository disbursements;
    private final RailCallbackDedupStore dedup;

    public CallbackClassifier(DisbursementRepository disbursements, RailCallbackDedupStore dedup) {
        this.disbursements = Objects.requireNonNull(disbursements);
        this.dedup = Objects.requireNonNull(dedup);
    }

    public Result classify(UUID loanId, String railRef, RailStatus incoming) {
        if (!dedup.firstSeen(railRef, incoming.name())) {
            return Result.DUPLICATE;
        }
        Optional<Disbursement> disbursement = disbursements.findByLoanId(loanId);
        if (disbursement.isEmpty()) {
            return Result.UNKNOWN_DISBURSEMENT;
        }
        CallbackResolution resolution =
                DisbursementStateMachine.resolve(disbursement.get().status(), incoming);
        return switch (resolution) {
            case APPLIED -> Result.APPLIED;
            case DUPLICATE -> Result.DUPLICATE;
            case CONFLICT -> Result.CONFLICT;
        };
    }
}
