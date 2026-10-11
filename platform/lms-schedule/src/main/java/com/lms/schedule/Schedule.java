package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A full reducing-balance amortisation schedule (docs/domain-rules.md "Schedule engine"). Before
 * disbursement a loan holds an indicative schedule flagged {@code provisional}; the actual schedule
 * is regenerated on the real disbursement date (rule 6).
 *
 * @param principal the financed principal P
 * @param annualInterestRate nominal APR used to price the schedule
 * @param tenor instalment count
 * @param anchorDate the date instalments are measured from (disbursement date, or an indicative
 *     one)
 * @param emi the level EMI (the last instalment may differ by the rounding residue)
 * @param items the per-instalment lines, in order
 * @param totalInterest sum of all interest portions
 * @param provisional true when priced against an indicative date before disbursement
 */
public record Schedule(
        Money principal,
        Rate annualInterestRate,
        int tenor,
        LocalDate anchorDate,
        Money emi,
        List<ScheduleItem> items,
        Money totalInterest,
        boolean provisional) {

    public Schedule {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(annualInterestRate, "annualInterestRate");
        Objects.requireNonNull(anchorDate, "anchorDate");
        Objects.requireNonNull(emi, "emi");
        Objects.requireNonNull(totalInterest, "totalInterest");
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        if (items.size() != tenor) {
            throw new IllegalArgumentException("items size must equal tenor");
        }
    }

    /**
     * Total amount the borrower repays over the life of the loan ({@code principal + interest}).
     */
    public Money totalPayable() {
        return principal.add(totalInterest);
    }
}
