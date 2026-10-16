package com.lms.disbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisbursementStateMachineTest {

    @Test
    void firstResultForInFlightDisbursementIsApplied() {
        assertThat(
                        DisbursementStateMachine.resolve(
                                DisbursementStatus.INITIATED, RailStatus.SUCCESS))
                .isEqualTo(CallbackResolution.APPLIED);
        assertThat(
                        DisbursementStateMachine.resolve(
                                DisbursementStatus.INITIATED, RailStatus.FAILED))
                .isEqualTo(CallbackResolution.APPLIED);
    }

    @Test
    void lateResultCanStillResolveAManualReview() {
        assertThat(
                        DisbursementStateMachine.resolve(
                                DisbursementStatus.MANUAL_REVIEW, RailStatus.SUCCESS))
                .isEqualTo(CallbackResolution.APPLIED);
    }

    @Test
    void duplicateTerminalResultIsIgnored() {
        assertThat(
                        DisbursementStateMachine.resolve(
                                DisbursementStatus.SUCCEEDED, RailStatus.SUCCESS))
                .isEqualTo(CallbackResolution.DUPLICATE);
        assertThat(DisbursementStateMachine.resolve(DisbursementStatus.FAILED, RailStatus.FAILED))
                .isEqualTo(CallbackResolution.DUPLICATE);
    }

    @Test
    void conflictingTerminalResultIsFlagged() {
        // Success already recorded, a failure callback arrives late: leave SUCCEEDED, count
        // conflict.
        assertThat(
                        DisbursementStateMachine.resolve(
                                DisbursementStatus.SUCCEEDED, RailStatus.FAILED))
                .isEqualTo(CallbackResolution.CONFLICT);
        assertThat(DisbursementStateMachine.resolve(DisbursementStatus.FAILED, RailStatus.SUCCESS))
                .isEqualTo(CallbackResolution.CONFLICT);
    }
}
