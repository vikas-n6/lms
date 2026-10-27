package com.lms.reporting.application;

import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.events.MoneyAmount;
import com.lms.contracts.loan.LoanActivated;
import com.lms.contracts.loan.LoanCancelled;
import com.lms.contracts.loan.LoanClosed;
import com.lms.contracts.loan.LoanCreated;
import com.lms.contracts.loan.LoanDpdUpdated;
import com.lms.contracts.loan.LoanForeclosed;
import com.lms.contracts.loan.LoanInstallmentDueSoon;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.loan.LoanPenalAccrued;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.contracts.loan.LoanWrittenOff;
import com.lms.money.Money;
import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.application.port.out.CollectionCounterRepository;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.domain.ApplicationFunnelRow;
import com.lms.reporting.domain.DpdBucketCategory;
import com.lms.reporting.domain.LoanSnapshotRow;
import java.time.YearMonth;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Projects loan.events and disbursement.events onto {@code loan_read} and the collection counters
 * (ADR 0013). Each handler loads the current row (or a skeleton), applies the change and upserts —
 * so projection is a pure function of the ordered event stream and a replay from offset 0 rebuilds
 * the same rows. The loan state is inferred from the stream (reporting consumes no explicit state
 * event): a terminal event pins a terminal state, otherwise DPD decides Active vs Delinquent.
 */
public class LoanProjection {

    private final LoanReadRepository loans;
    private final CollectionCounterRepository counters;
    private final ApplicationReadRepository applications;

    public LoanProjection(
            LoanReadRepository loans,
            CollectionCounterRepository counters,
            ApplicationReadRepository applications) {
        this.loans = Objects.requireNonNull(loans);
        this.counters = Objects.requireNonNull(counters);
        this.applications = Objects.requireNonNull(applications);
    }

    public void onCreated(LoanCreated e) {
        Money principal = money(e.getPrincipal());
        Currency ccy = principal.currency();
        LoanSnapshotRow row =
                new LoanSnapshotRow(
                        UUID.fromString(e.getLoanId()),
                        e.getCustomerId(),
                        e.getProductVersion(),
                        "PendingDisbursal",
                        0,
                        DpdBucketCategory.CURRENT,
                        principal,
                        Money.zero(ccy),
                        Money.zero(ccy),
                        null,
                        null);
        loans.save(row, e.getApplicationId());
    }

    public void onActivated(LoanActivated e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        loans.find(loanId)
                .ifPresent(
                        row -> {
                            String month = YearMonth.from(e.getDisbursementDate()).toString();
                            save(withState(withMonth(row, month), "Active"), loanId);
                            loans.findApplicationId(loanId)
                                    .ifPresent(
                                            appId ->
                                                    applications.merge(
                                                            appId, null, disbursedFlag()));
                        });
    }

    public void onDisbursementSucceeded(DisbursementSucceeded e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        loans.find(loanId)
                .ifPresent(
                        row -> {
                            Money net = money(e.getNetAmount());
                            String month = YearMonth.from(e.getDisbursementDate()).toString();
                            LoanSnapshotRow updated =
                                    new LoanSnapshotRow(
                                            row.loanId(),
                                            row.customerId(),
                                            row.productVersion(),
                                            row.state(),
                                            row.dpd(),
                                            row.bucket(),
                                            row.principalOutstanding(),
                                            row.interestOutstanding(),
                                            row.penalOutstanding(),
                                            net,
                                            month);
                            save(updated, loanId);
                        });
    }

    public void onInterestAccrued(LoanInterestAccrued e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        loans.find(loanId).ifPresent(row -> save(addInterest(row, money(e.getAmount())), loanId));
    }

    public void onPenalAccrued(LoanPenalAccrued e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        Money amount = money(e.getAmount());
        loans.find(loanId).ifPresent(row -> save(addPenal(row, amount), loanId));
        counters.addBilled(amount); // penal charged is part of demand
    }

    public void onInstallmentDueSoon(LoanInstallmentDueSoon e) {
        counters.addBilled(money(e.getAmountDue())); // the instalment becoming due is demand
    }

    public void onRepaymentAllocated(LoanRepaymentAllocated e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        Money penal = money(e.getPenal());
        Money interest = money(e.getInterest());
        Money principal = money(e.getPrincipal());
        loans.find(loanId)
                .ifPresent(row -> save(applyAllocation(row, principal, interest, penal), loanId));
        counters.addCollected(penal.add(interest).add(principal)); // excludes advance
    }

    public void onRepaymentReversed(LoanRepaymentReversed e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        Money penal = money(e.getPenal());
        Money interest = money(e.getInterest());
        Money principal = money(e.getPrincipal());
        // A reversal re-adds the balances the allocation had cleared and backs out the collection.
        loans.find(loanId)
                .ifPresent(
                        row ->
                                save(
                                        applyAllocation(
                                                row,
                                                principal.negate(),
                                                interest.negate(),
                                                penal.negate()),
                                        loanId));
        counters.addCollected(penal.add(interest).add(principal).negate());
    }

    public void onDpdUpdated(LoanDpdUpdated e) {
        UUID loanId = UUID.fromString(e.getLoanId());
        loans.find(loanId)
                .ifPresent(
                        row -> {
                            int dpd = e.getDaysPastDue();
                            DpdBucketCategory bucket = DpdBucketCategory.parse(e.getBucket());
                            String state =
                                    isTerminal(row.state())
                                            ? row.state()
                                            : (dpd > 0 ? "Delinquent" : "Active");
                            LoanSnapshotRow updated =
                                    new LoanSnapshotRow(
                                            row.loanId(),
                                            row.customerId(),
                                            row.productVersion(),
                                            state,
                                            dpd,
                                            bucket,
                                            row.principalOutstanding(),
                                            row.interestOutstanding(),
                                            row.penalOutstanding(),
                                            row.disbursedAmount(),
                                            row.disbursalMonth());
                            save(updated, loanId);
                        });
    }

    public void onClosed(LoanClosed e) {
        terminal(UUID.fromString(e.getLoanId()), "Closed", false);
    }

    public void onForeclosed(LoanForeclosed e) {
        terminal(UUID.fromString(e.getLoanId()), "Foreclosed", false);
    }

    public void onCancelled(LoanCancelled e) {
        terminal(UUID.fromString(e.getLoanId()), "Cancelled", false);
    }

    public void onWrittenOff(LoanWrittenOff e) {
        // Write-off removes the receivable: zero the outstanding and pin the terminal state.
        terminal(UUID.fromString(e.getLoanId()), "WrittenOff", true);
    }

    private void terminal(UUID loanId, String state, boolean zeroBalances) {
        loans.find(loanId)
                .ifPresent(
                        row -> {
                            Currency ccy = row.principalOutstanding().currency();
                            LoanSnapshotRow updated =
                                    new LoanSnapshotRow(
                                            row.loanId(),
                                            row.customerId(),
                                            row.productVersion(),
                                            state,
                                            0,
                                            DpdBucketCategory.CURRENT,
                                            zeroBalances
                                                    ? Money.zero(ccy)
                                                    : row.principalOutstanding(),
                                            zeroBalances
                                                    ? Money.zero(ccy)
                                                    : row.interestOutstanding(),
                                            zeroBalances ? Money.zero(ccy) : row.penalOutstanding(),
                                            row.disbursedAmount(),
                                            row.disbursalMonth());
                            save(updated, loanId);
                        });
    }

    private void save(LoanSnapshotRow row, UUID loanId) {
        loans.save(row, loans.findApplicationId(loanId).orElse(null));
    }

    private static boolean isTerminal(String state) {
        return switch (state) {
            case "Closed", "Foreclosed", "WrittenOff", "Cancelled" -> true;
            default -> false;
        };
    }

    private static ApplicationFunnelRow disbursedFlag() {
        return new ApplicationFunnelRow(null, false, false, false, false, true, false, false);
    }

    private static LoanSnapshotRow withMonth(LoanSnapshotRow row, String month) {
        return new LoanSnapshotRow(
                row.loanId(),
                row.customerId(),
                row.productVersion(),
                row.state(),
                row.dpd(),
                row.bucket(),
                row.principalOutstanding(),
                row.interestOutstanding(),
                row.penalOutstanding(),
                row.disbursedAmount(),
                month);
    }

    private static LoanSnapshotRow withState(LoanSnapshotRow row, String state) {
        return new LoanSnapshotRow(
                row.loanId(),
                row.customerId(),
                row.productVersion(),
                state,
                row.dpd(),
                row.bucket(),
                row.principalOutstanding(),
                row.interestOutstanding(),
                row.penalOutstanding(),
                row.disbursedAmount(),
                row.disbursalMonth());
    }

    private static LoanSnapshotRow addInterest(LoanSnapshotRow row, Money delta) {
        return new LoanSnapshotRow(
                row.loanId(),
                row.customerId(),
                row.productVersion(),
                row.state(),
                row.dpd(),
                row.bucket(),
                row.principalOutstanding(),
                row.interestOutstanding().add(delta),
                row.penalOutstanding(),
                row.disbursedAmount(),
                row.disbursalMonth());
    }

    private static LoanSnapshotRow addPenal(LoanSnapshotRow row, Money delta) {
        return new LoanSnapshotRow(
                row.loanId(),
                row.customerId(),
                row.productVersion(),
                row.state(),
                row.dpd(),
                row.bucket(),
                row.principalOutstanding(),
                row.interestOutstanding(),
                row.penalOutstanding().add(delta),
                row.disbursedAmount(),
                row.disbursalMonth());
    }

    private static LoanSnapshotRow applyAllocation(
            LoanSnapshotRow row, Money principal, Money interest, Money penal) {
        return new LoanSnapshotRow(
                row.loanId(),
                row.customerId(),
                row.productVersion(),
                row.state(),
                row.dpd(),
                row.bucket(),
                row.principalOutstanding().subtract(principal),
                row.interestOutstanding().subtract(interest),
                row.penalOutstanding().subtract(penal),
                row.disbursedAmount(),
                row.disbursalMonth());
    }

    private static Money money(MoneyAmount a) {
        return Money.of(a.getMinorUnits(), Currency.getInstance(a.getCurrency()));
    }
}
