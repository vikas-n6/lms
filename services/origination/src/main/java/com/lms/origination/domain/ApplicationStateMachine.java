package com.lms.origination.domain;

import com.lms.origination.domain.ApplicationStatus.Accepted;
import com.lms.origination.domain.ApplicationStatus.Decisioned;
import com.lms.origination.domain.ApplicationStatus.Draft;
import com.lms.origination.domain.ApplicationStatus.Expired;
import com.lms.origination.domain.ApplicationStatus.Offered;
import com.lms.origination.domain.ApplicationStatus.Rejected;
import com.lms.origination.domain.ApplicationStatus.Submitted;
import com.lms.origination.domain.ApplicationStatus.Withdrawn;
import com.lms.origination.domain.ApplicationTrigger.Accept;
import com.lms.origination.domain.ApplicationTrigger.Expire;
import com.lms.origination.domain.ApplicationTrigger.MakeOffer;
import com.lms.origination.domain.ApplicationTrigger.RecordDecision;
import com.lms.origination.domain.ApplicationTrigger.Reject;
import com.lms.origination.domain.ApplicationTrigger.Submit;
import com.lms.origination.domain.ApplicationTrigger.Withdraw;

/**
 * The application lifecycle as a pure function {@code (status, trigger) -> TransitionResult}, with
 * an exhaustive switch and no {@code default} over the sealed state and trigger types (docs/domain-
 * rules.md state-machine discipline). Illegal transitions return {@link TransitionResult.Illegal}
 * and are rejected by the caller, never silently ignored.
 *
 * <p>Legal transitions:
 *
 * <pre>
 *   DRAFT      --submit-->         SUBMITTED
 *   DRAFT      --withdraw-->       WITHDRAWN
 *   SUBMITTED  --recordDecision--> DECISIONED
 *   SUBMITTED  --withdraw-->       WITHDRAWN
 *   DECISIONED --recordDecision--> DECISIONED   (idempotent re-decision / manual override)
 *   DECISIONED --makeOffer-->      OFFERED      (APPROVE, or admin override to approve)
 *   DECISIONED --reject-->         REJECTED     (REJECT, or admin override to reject)
 *   DECISIONED --withdraw-->       WITHDRAWN
 *   OFFERED    --accept-->         ACCEPTED
 *   OFFERED    --expire-->         EXPIRED
 *   OFFERED    --withdraw-->       WITHDRAWN
 * </pre>
 *
 * Terminal states (ACCEPTED, REJECTED, EXPIRED, WITHDRAWN) accept no triggers.
 */
public final class ApplicationStateMachine {

    private ApplicationStateMachine() {}

    public static TransitionResult transition(ApplicationStatus from, ApplicationTrigger trigger) {
        ApplicationStatus next =
                switch (from) {
                    case Draft ignored -> fromDraft(trigger);
                    case Submitted ignored -> fromSubmitted(trigger);
                    case Decisioned ignored -> fromDecisioned(trigger);
                    case Offered ignored -> fromOffered(trigger);
                    case Accepted ignored -> null;
                    case Rejected ignored -> null;
                    case Expired ignored -> null;
                    case Withdrawn ignored -> null;
                };
        return next == null
                ? new TransitionResult.Illegal(from, trigger)
                : new TransitionResult.Moved(from, trigger, next);
    }

    private static ApplicationStatus fromDraft(ApplicationTrigger trigger) {
        return switch (trigger) {
            case Submit ignored -> new Submitted();
            case Withdraw ignored -> new Withdrawn();
            case RecordDecision ignored -> null;
            case MakeOffer ignored -> null;
            case Reject ignored -> null;
            case Accept ignored -> null;
            case Expire ignored -> null;
        };
    }

    private static ApplicationStatus fromSubmitted(ApplicationTrigger trigger) {
        return switch (trigger) {
            case RecordDecision ignored -> new Decisioned();
            case Withdraw ignored -> new Withdrawn();
            case Submit ignored -> null;
            case MakeOffer ignored -> null;
            case Reject ignored -> null;
            case Accept ignored -> null;
            case Expire ignored -> null;
        };
    }

    private static ApplicationStatus fromDecisioned(ApplicationTrigger trigger) {
        return switch (trigger) {
            case RecordDecision ignored -> new Decisioned();
            case MakeOffer ignored -> new Offered();
            case Reject ignored -> new Rejected();
            case Withdraw ignored -> new Withdrawn();
            case Submit ignored -> null;
            case Accept ignored -> null;
            case Expire ignored -> null;
        };
    }

    private static ApplicationStatus fromOffered(ApplicationTrigger trigger) {
        return switch (trigger) {
            case Accept ignored -> new Accepted();
            case Expire ignored -> new Expired();
            case Withdraw ignored -> new Withdrawn();
            case Submit ignored -> null;
            case RecordDecision ignored -> null;
            case MakeOffer ignored -> null;
            case Reject ignored -> null;
        };
    }
}
