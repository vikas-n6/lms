package com.lms.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KycStatusTest {

    @Test
    void allowedTransitions() {
        assertThat(KycStatus.NOT_STARTED.transitionTo(KycStatus.PENDING))
                .isEqualTo(KycStatus.PENDING);
        assertThat(KycStatus.NOT_STARTED.transitionTo(KycStatus.VERIFIED))
                .isEqualTo(KycStatus.VERIFIED);
        assertThat(KycStatus.PENDING.transitionTo(KycStatus.FAILED)).isEqualTo(KycStatus.FAILED);
    }

    @Test
    void terminalStatesAreTerminal() {
        assertThat(KycStatus.VERIFIED.isTerminal()).isTrue();
        assertThat(KycStatus.FAILED.isTerminal()).isTrue();
        assertThat(KycStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThat(KycStatus.NOT_STARTED.canTransitionTo(KycStatus.NOT_STARTED)).isFalse();
        assertThatThrownBy(() -> KycStatus.VERIFIED.transitionTo(KycStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> KycStatus.FAILED.transitionTo(KycStatus.VERIFIED))
                .isInstanceOf(IllegalStateException.class);
    }
}
