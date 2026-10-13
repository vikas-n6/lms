package com.lms.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.loanaccount.domain.LoanState.Active;
import com.lms.loanaccount.domain.LoanState.Cancelled;
import com.lms.loanaccount.domain.LoanState.Closed;
import com.lms.loanaccount.domain.LoanState.Created;
import com.lms.loanaccount.domain.LoanState.Delinquent;
import com.lms.loanaccount.domain.LoanState.Foreclosed;
import com.lms.loanaccount.domain.LoanState.PendingDisbursal;
import com.lms.loanaccount.domain.LoanState.WrittenOff;
import com.lms.loanaccount.domain.LoanTransition.CancelDisbursement;
import com.lms.loanaccount.domain.LoanTransition.ClearOverdue;
import com.lms.loanaccount.domain.LoanTransition.ConfirmDisbursement;
import com.lms.loanaccount.domain.LoanTransition.DraftSchedule;
import com.lms.loanaccount.domain.LoanTransition.Foreclose;
import com.lms.loanaccount.domain.LoanTransition.MarkDelinquent;
import com.lms.loanaccount.domain.LoanTransition.PayInFull;
import com.lms.loanaccount.domain.LoanTransition.WriteOff;
import com.lms.loanaccount.domain.LoanTransitionResult.Illegal;
import com.lms.loanaccount.domain.LoanTransitionResult.Moved;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The loan state machine (docs/domain-rules.md "Loan state machine"): every transition proven. */
class LoanStateMachineTest {

    private static LoanState to(LoanState from, LoanTransition trigger) {
        LoanTransitionResult result = LoanStateMachine.transition(from, trigger);
        assertThat(result).isInstanceOf(Moved.class);
        return ((Moved) result).to();
    }

    private static Illegal illegal(LoanState from, LoanTransition trigger) {
        LoanTransitionResult result = LoanStateMachine.transition(from, trigger);
        assertThat(result).isInstanceOf(Illegal.class);
        assertThat(result.isLegal()).isFalse();
        return (Illegal) result;
    }

    @Test
    void theHappyPathCreatedToClosed() {
        assertThat(to(new Created(), new DraftSchedule(true))).isEqualTo(new PendingDisbursal());
        assertThat(to(new PendingDisbursal(), new ConfirmDisbursement(true)))
                .isEqualTo(new Active());
        assertThat(to(new Active(), new PayInFull(true))).isEqualTo(new Closed());
    }

    @Test
    void delinquencyRoundTripAndWriteOff() {
        assertThat(to(new Active(), new MarkDelinquent(1))).isEqualTo(new Delinquent());
        assertThat(to(new Delinquent(), new ClearOverdue(true))).isEqualTo(new Active());
        assertThat(to(new Delinquent(), new WriteOff(true, true))).isEqualTo(new WrittenOff());
    }

    @Test
    void cancellationAndForeclosure() {
        assertThat(to(new PendingDisbursal(), new CancelDisbursement(true)))
                .isEqualTo(new Cancelled());
        assertThat(to(new Active(), new Foreclose(true))).isEqualTo(new Foreclosed());
        assertThat(to(new Delinquent(), new Foreclose(true))).isEqualTo(new Foreclosed());
        assertThat(to(new Delinquent(), new PayInFull(true))).isEqualTo(new Closed());
    }

    @Test
    void guardFailuresAreIllegalAndLeaveStateUnchanged() {
        assertThat(illegal(new Created(), new DraftSchedule(false)).reason())
                .contains("no provisional schedule");
        assertThat(illegal(new PendingDisbursal(), new ConfirmDisbursement(false)).reason())
                .contains("does not match");
        assertThat(illegal(new PendingDisbursal(), new CancelDisbursement(false)).reason())
                .contains("compensation");
        assertThat(illegal(new Active(), new MarkDelinquent(0)).reason()).contains("no overdue");
        assertThat(illegal(new Delinquent(), new ClearOverdue(false)).reason())
                .contains("overdue total");
        assertThat(illegal(new Active(), new PayInFull(false)).reason()).contains("outstanding");
        assertThat(illegal(new Active(), new Foreclose(false)).reason()).contains("foreclosure");
        assertThat(illegal(new Delinquent(), new WriteOff(false, true)).reason())
                .contains("DPD threshold");
        assertThat(illegal(new Delinquent(), new WriteOff(true, false)).reason())
                .contains("approval record");
    }

    @Test
    void illegalPairingsAreRejected() {
        // A few representative illegal (state, trigger) pairs.
        illegal(new Created(), new ConfirmDisbursement(true));
        illegal(new Created(), new PayInFull(true));
        illegal(new PendingDisbursal(), new MarkDelinquent(3));
        illegal(new Active(), new ConfirmDisbursement(true));
        illegal(new Active(), new ClearOverdue(true)); // not delinquent
        illegal(new Active(), new WriteOff(true, true)); // write-off only from Delinquent
        illegal(new Delinquent(), new MarkDelinquent(2)); // already delinquent
        illegal(new Delinquent(), new ConfirmDisbursement(true));
    }

    @Test
    void terminalStatesAcceptNoTransitions() {
        List<LoanState> terminals =
                List.of(new Closed(), new Foreclosed(), new WrittenOff(), new Cancelled());
        List<LoanTransition> triggers =
                List.of(
                        new DraftSchedule(true),
                        new ConfirmDisbursement(true),
                        new CancelDisbursement(true),
                        new MarkDelinquent(1),
                        new ClearOverdue(true),
                        new PayInFull(true),
                        new Foreclose(true),
                        new WriteOff(true, true));
        for (LoanState terminal : terminals) {
            assertThat(terminal.isTerminal()).isTrue();
            for (LoanTransition trigger : triggers) {
                illegal(terminal, trigger);
            }
        }
    }

    @Test
    void stateCodesRoundTrip() {
        for (LoanState state :
                List.of(
                        new Created(),
                        new PendingDisbursal(),
                        new Active(),
                        new Delinquent(),
                        new Closed(),
                        new Foreclosed(),
                        new WrittenOff(),
                        new Cancelled())) {
            assertThat(LoanState.fromCode(state.code())).isEqualTo(state);
        }
    }
}
