package com.lms.simulators.bank;

import com.lms.simulators.behavior.SimBehavior;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bank simulator (phase prompt S2): penny-drop verification and statement-analysis income
 * estimation. Both results are deterministic in the inputs so scenarios repeat. The currency comes
 * from configuration, never a literal (CLAUDE.md money rules).
 */
@Component
public class BankSimulator {

    private static final int MIN_INCOME_MAJOR = 10_000;
    private static final int INCOME_SPAN_MAJOR = 70_000;
    private static final int MINOR_PER_MAJOR_INR = 100;

    private final SimBehavior behavior;
    private final String defaultCurrency;

    public BankSimulator(
            SimBehavior behavior, @Value("${lms.currency.default:INR}") String defaultCurrency) {
        this.behavior = behavior;
        this.defaultCurrency = defaultCurrency;
    }

    /** Penny-drop: fails when the account number ends with 0000, otherwise verifies. */
    public PennyDropResult pennyDrop(String accountNumber, String ifsc) {
        behavior.gate("bank");
        boolean verified = accountNumber != null && !accountNumber.endsWith("0000");
        return new PennyDropResult(verified, mask(accountNumber), ifsc);
    }

    /** Estimate monthly income deterministically from the subject id. */
    public StatementAnalysis analyzeStatements(String subjectId) {
        behavior.gate("bank");
        int hash = Math.floorMod(String.valueOf(subjectId).hashCode(), INCOME_SPAN_MAJOR + 1);
        long minorUnits = (long) (MIN_INCOME_MAJOR + hash) * MINOR_PER_MAJOR_INR;
        return new StatementAnalysis(minorUnits, defaultCurrency);
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "XXXX";
        }
        return "XXXXXX" + accountNumber.substring(accountNumber.length() - 4);
    }

    public record PennyDropResult(boolean verified, String maskedAccountNumber, String ifsc) {}

    public record StatementAnalysis(long estimatedMonthlyIncomeMinor, String currency) {}
}
