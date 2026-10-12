package com.lms.simulators.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.bank.BankController.PennyDropRequest;
import com.lms.simulators.bank.BankController.StatementRequest;
import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import org.junit.jupiter.api.Test;

class BankControllerTest {

    private final BankController controller =
            new BankController(new BankSimulator(new SimBehavior(new SimConfigRegistry()), "INR"));

    @Test
    void pennyDropAndStatementAnalysis() {
        assertThat(controller.pennyDrop(new PennyDropRequest("123456789", "HDFC0001")).verified())
                .isTrue();
        assertThat(controller.statementAnalysis(new StatementRequest("s1")).currency())
                .isEqualTo("INR");
    }
}
