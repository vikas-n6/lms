package com.lms.decision.application;

import com.lms.decision.domain.DecisionInputs;
import com.lms.money.Amortization;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.math.RoundingMode;

/**
 * Builds the pure {@link DecisionInputs} a rule set evaluates, from the application facts, the
 * bureau/bank fan-out result and the velocity count (phase prompt S5).
 *
 * <p>The affordability "new EMI" is computed with the schedule maths for the requested amount at
 * the assessment tenor and APR (both from configuration, not hard-coded). The assessment tenor is
 * the longest offered tenor — the lowest EMI — so an applicant is not rejected for a tenor they
 * would not pick.
 */
public final class DecisionInputsAssembler {

    private static final int MONTHS_PER_YEAR = 12;

    private DecisionInputsAssembler() {}

    public static DecisionInputs assemble(
            ApplicationForDecision app,
            MarketData market,
            int applicationsInWindow,
            Rate assessmentApr,
            int assessmentTenor) {
        Money newEmi =
                Amortization.emi(
                        app.requestedAmount(),
                        assessmentApr.dividedBy(MONTHS_PER_YEAR),
                        assessmentTenor,
                        RoundingMode.HALF_UP);
        return new DecisionInputs(
                app.ageYears(),
                app.kycVerified(),
                market.bureauAvailable(),
                market.bureauScore(),
                market.bureauOverdue(),
                app.declaredMonthlyIncome(),
                app.existingMonthlyEmi(),
                newEmi,
                app.activeLoanCount(),
                applicationsInWindow);
    }
}
