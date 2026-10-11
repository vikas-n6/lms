package com.lms.decision.domain;

import com.lms.money.Money;
import java.util.Objects;

/**
 * The immutable snapshot a decision is evaluated against (docs/domain-rules.md "Eligibility
 * rules"). It is stored verbatim with the decision so re-evaluating it against the same rule set
 * version yields an identical outcome (replay). All figures are derived upstream (age from DOB with
 * the injected Clock; {@code newMonthlyEmi} from the schedule engine; {@code applicationsInWindow}
 * from the Redis velocity window) so the rules themselves are pure comparisons.
 *
 * @param ageYears applicant age in whole years at submit
 * @param kycVerified true only when KYC status is VERIFIED
 * @param bureauAvailable false when the bureau pull failed after retries
 * @param bureauScore bureau score (300..900); meaningful only when {@code bureauAvailable}
 * @param bureauHasOverdueAccount true when the bureau reports an active overdue account
 * @param declaredMonthlyIncome declared monthly income
 * @param existingMonthlyEmi sum of existing EMIs with us (0 in Phase 1)
 * @param newMonthlyEmi EMI of the application being assessed (from the schedule engine)
 * @param activeLoanCount active loans with us
 * @param applicationsInWindow applications from this phone in the velocity window, including this
 *     one
 */
public record DecisionInputs(
        int ageYears,
        boolean kycVerified,
        boolean bureauAvailable,
        int bureauScore,
        boolean bureauHasOverdueAccount,
        Money declaredMonthlyIncome,
        Money existingMonthlyEmi,
        Money newMonthlyEmi,
        int activeLoanCount,
        int applicationsInWindow) {

    public DecisionInputs {
        Objects.requireNonNull(declaredMonthlyIncome, "declaredMonthlyIncome");
        Objects.requireNonNull(existingMonthlyEmi, "existingMonthlyEmi");
        Objects.requireNonNull(newMonthlyEmi, "newMonthlyEmi");
    }
}
