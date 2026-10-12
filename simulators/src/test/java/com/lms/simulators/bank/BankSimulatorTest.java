package com.lms.simulators.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.simulators.behavior.SimBehavior;
import com.lms.simulators.config.SimConfigRegistry;
import org.junit.jupiter.api.Test;

class BankSimulatorTest {

    private final BankSimulator bank =
            new BankSimulator(new SimBehavior(new SimConfigRegistry()), "INR");

    @Test
    void pennyDropFailsForAccountsEndingInZeros() {
        assertThat(bank.pennyDrop("123456789", "HDFC0001").verified()).isTrue();
        assertThat(bank.pennyDrop("123450000", "HDFC0001").verified()).isFalse();
    }

    @Test
    void pennyDropMasksTheAccountNumber() {
        assertThat(bank.pennyDrop("123456789", "HDFC0001").maskedAccountNumber())
                .isEqualTo("XXXXXX6789");
    }

    @Test
    void statementAnalysisIsDeterministicAndCarriesTheConfiguredCurrency() {
        BankSimulator.StatementAnalysis a = bank.analyzeStatements("subject-abc");
        BankSimulator.StatementAnalysis b = bank.analyzeStatements("subject-abc");
        assertThat(a).isEqualTo(b);
        assertThat(a.currency()).isEqualTo("INR");
        // 10,000.00 .. 80,000.00 in paise.
        assertThat(a.estimatedMonthlyIncomeMinor()).isBetween(1_000_000L, 8_000_000L);
    }
}
