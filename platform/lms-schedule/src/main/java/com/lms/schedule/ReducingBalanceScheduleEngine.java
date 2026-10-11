package com.lms.schedule;

import com.lms.money.Amortization;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducing-balance EMI schedule engine (docs/domain-rules.md "Schedule engine"). Pure: no Spring,
 * no clock, no I/O. All money arithmetic goes through {@link Money}/{@link Amortization}; this
 * class never touches BigDecimal (ArchUnit enforces that outside lms-money).
 */
public final class ReducingBalanceScheduleEngine implements ScheduleEngine {

    private static final int MONTHS_PER_YEAR = 12;

    @Override
    public Schedule generate(
            Money principal,
            Rate annualRate,
            int tenor,
            LocalDate anchorDate,
            boolean provisional) {
        if (tenor <= 0) {
            throw new IllegalArgumentException("tenor must be positive");
        }
        if (!principal.isPositive()) {
            throw new IllegalArgumentException("principal must be positive");
        }
        Rate monthlyRate = annualRate.dividedBy(MONTHS_PER_YEAR);
        Money emi = Amortization.emi(principal, monthlyRate, tenor, RoundingMode.HALF_UP);

        List<ScheduleItem> items = new ArrayList<>(tenor);
        Money outstanding = principal;
        Money totalInterest = Money.zero(principal.currency());

        for (int k = 1; k <= tenor; k++) {
            // Rule 2: interest = round(outstanding * monthly rate).
            Money interest = outstanding.percentOf(monthlyRate, RoundingMode.HALF_UP);
            Money principalPortion;
            Money amount;
            if (k < tenor) {
                principalPortion = emi.subtract(interest);
                amount = emi;
            } else {
                // Rule 3: the last instalment clears the residue exactly.
                principalPortion = outstanding;
                amount = outstanding.add(interest);
            }
            outstanding = outstanding.subtract(principalPortion);
            totalInterest = totalInterest.add(interest);
            LocalDate dueDate = DueDates.addMonthsClamped(anchorDate, k);
            items.add(
                    new ScheduleItem(k, dueDate, amount, principalPortion, interest, outstanding));
        }

        return new Schedule(
                principal, annualRate, tenor, anchorDate, emi, items, totalInterest, provisional);
    }

    @Override
    public OfferQuote quote(Product product, Money principal, int tenor, LocalDate anchorDate) {
        if (!product.supportsPrincipal(principal)) {
            throw new IllegalArgumentException("principal is outside the product range or step");
        }
        if (!product.tenors().contains(tenor)) {
            throw new IllegalArgumentException("tenor is not offered by the product: " + tenor);
        }
        Schedule schedule =
                generate(principal, product.annualInterestRate(), tenor, anchorDate, true);

        Money fee = principal.percentOf(product.processingFeeRate(), RoundingMode.HALF_UP);
        Money gst = fee.percentOf(product.gstRate(), RoundingMode.HALF_UP);
        Money netDisbursal = principal.subtract(fee).subtract(gst);

        List<Money> cashflows = new ArrayList<>(tenor);
        for (ScheduleItem item : schedule.items()) {
            cashflows.add(item.amount());
        }
        Rate effectiveApr =
                Amortization.effectiveAnnualRate(netDisbursal, cashflows, MONTHS_PER_YEAR);

        return new OfferQuote(
                principal, tenor, schedule.emi(), fee, gst, netDisbursal, effectiveApr, schedule);
    }
}
