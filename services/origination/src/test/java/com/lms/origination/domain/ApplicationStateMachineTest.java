package com.lms.origination.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exhaustive transition tests for the application state machine (phase prompt S4). */
class ApplicationStateMachineTest {

    private static ApplicationStatus move(ApplicationStatus from, ApplicationTrigger t) {
        TransitionResult r = ApplicationStateMachine.transition(from, t);
        assertThat(r).isInstanceOf(TransitionResult.Moved.class);
        return ((TransitionResult.Moved) r).to();
    }

    private static void illegal(ApplicationStatus from, ApplicationTrigger t) {
        TransitionResult r = ApplicationStateMachine.transition(from, t);
        assertThat(r).isInstanceOf(TransitionResult.Illegal.class);
        assertThat(r.isLegal()).isFalse();
    }

    @Test
    void happyPathDraftToAccepted() {
        ApplicationStatus s = new Draft();
        s = move(s, new Submit());
        assertThat(s).isInstanceOf(Submitted.class);
        s = move(s, new RecordDecision());
        assertThat(s).isInstanceOf(Decisioned.class);
        s = move(s, new MakeOffer());
        assertThat(s).isInstanceOf(Offered.class);
        s = move(s, new Accept());
        assertThat(s).isInstanceOf(Accepted.class);
        assertThat(s.isTerminal()).isTrue();
    }

    @Test
    void decisionedCanRejectOrReDecide() {
        assertThat(move(new Decisioned(), new Reject())).isInstanceOf(Rejected.class);
        // Idempotent re-decision (e.g. duplicate event, or an admin override replacing a REFER).
        assertThat(move(new Decisioned(), new RecordDecision())).isInstanceOf(Decisioned.class);
    }

    @Test
    void offerCanExpire() {
        assertThat(move(new Offered(), new Expire())).isInstanceOf(Expired.class);
    }

    @Test
    void withdrawAllowedBeforeAcceptance() {
        assertThat(move(new Draft(), new Withdraw())).isInstanceOf(Withdrawn.class);
        assertThat(move(new Submitted(), new Withdraw())).isInstanceOf(Withdrawn.class);
        assertThat(move(new Decisioned(), new Withdraw())).isInstanceOf(Withdrawn.class);
        assertThat(move(new Offered(), new Withdraw())).isInstanceOf(Withdrawn.class);
    }

    @Test
    void illegalTransitionsAreRejected() {
        illegal(new Draft(), new Accept());
        illegal(new Draft(), new MakeOffer());
        illegal(new Submitted(), new Accept());
        illegal(new Submitted(), new MakeOffer());
        illegal(new Decisioned(), new Accept());
        illegal(new Offered(), new MakeOffer());
        illegal(new Offered(), new Submit());
    }

    @Test
    void terminalStatesRejectEveryTrigger() {
        List<ApplicationStatus> terminals =
                List.of(new Accepted(), new Rejected(), new Expired(), new Withdrawn());
        List<ApplicationTrigger> triggers =
                List.of(
                        new Submit(),
                        new RecordDecision(),
                        new MakeOffer(),
                        new Reject(),
                        new Accept(),
                        new Expire(),
                        new Withdraw());
        for (ApplicationStatus terminal : terminals) {
            assertThat(terminal.isTerminal()).isTrue();
            for (ApplicationTrigger trigger : triggers) {
                illegal(terminal, trigger);
            }
        }
    }

    @Test
    void codeRoundTrips() {
        List<ApplicationStatus> all =
                List.of(
                        new Draft(),
                        new Submitted(),
                        new Decisioned(),
                        new Offered(),
                        new Accepted(),
                        new Rejected(),
                        new Expired(),
                        new Withdrawn());
        for (ApplicationStatus s : all) {
            assertThat(ApplicationStatus.fromCode(s.code())).isEqualTo(s);
        }
    }
}
