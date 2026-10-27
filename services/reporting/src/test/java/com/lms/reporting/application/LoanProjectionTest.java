package com.lms.reporting.application;

import static com.lms.reporting.application.TestEvents.date;
import static com.lms.reporting.application.TestEvents.meta;
import static com.lms.reporting.application.TestEvents.money;
import static org.assertj.core.api.Assertions.assertThat;

import com.lms.contracts.disbursement.DisbursementSucceeded;
import com.lms.contracts.loan.LoanActivated;
import com.lms.contracts.loan.LoanCreated;
import com.lms.contracts.loan.LoanDpdUpdated;
import com.lms.contracts.loan.LoanInterestAccrued;
import com.lms.contracts.loan.LoanPenalAccrued;
import com.lms.contracts.loan.LoanRepaymentAllocated;
import com.lms.contracts.loan.LoanRepaymentReversed;
import com.lms.contracts.loan.LoanWrittenOff;
import com.lms.money.Money;
import com.lms.reporting.domain.DpdBucketCategory;
import com.lms.reporting.domain.LoanSnapshotRow;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanProjectionTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final UUID loanId = UUID.randomUUID();

    private ReportingFakes.InMemoryLoans loans;
    private ReportingFakes.InMemoryCounters counters;
    private ReportingFakes.InMemoryApplications apps;
    private LoanProjection projection;

    @BeforeEach
    void setUp() {
        loans = new ReportingFakes.InMemoryLoans();
        counters = new ReportingFakes.InMemoryCounters();
        apps = new ReportingFakes.InMemoryApplications();
        projection = new LoanProjection(loans, counters, apps);
    }

    private LoanCreated created() {
        return LoanCreated.newBuilder()
                .setMetadata(meta("loan.created", loanId.toString()))
                .setLoanId(loanId.toString())
                .setLoanNumber("LN-1")
                .setApplicationId("APP-1")
                .setCustomerId("CUST-1")
                .setProductId("MICRO")
                .setProductVersion(1)
                .setPrincipal(money(50_000_00))
                .setTenorMonths(12)
                .setEmi(money(4_735_00))
                .setFee(money(1_000_00))
                .setGst(money(180_00))
                .setNetDisbursal(money(48_820_00))
                .setAnnualInterestRatePercent("24.00")
                .setCreatedAt(java.time.Instant.parse("2026-01-15T00:00:00Z"))
                .build();
    }

    private LoanSnapshotRow row() {
        return loans.find(loanId).orElseThrow();
    }

    @Test
    void createdInsertsPendingDisbursalRowWithPrincipalOutstanding() {
        projection.onCreated(created());

        LoanSnapshotRow row = row();
        assertThat(row.state()).isEqualTo("PendingDisbursal");
        assertThat(row.principalOutstanding()).isEqualTo(Money.of(50_000_00, INR));
        assertThat(row.interestOutstanding()).isEqualTo(Money.zero(INR));
        assertThat(row.disbursalMonth()).isNull();
        assertThat(loans.findApplicationId(loanId)).contains("APP-1");
    }

    @Test
    void activationSetsActiveAndDisbursalMonthAndMarksApplicationDisbursed() {
        projection.onCreated(created());
        projection.onActivated(
                LoanActivated.newBuilder()
                        .setMetadata(meta("loan.activated", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setLoanNumber("LN-1")
                        .setDisbursementDate(date("2026-02-10"))
                        .setFirstDueDate(date("2026-03-10"))
                        .setMaturityDate(date("2027-02-10"))
                        .setActivatedAt(java.time.Instant.parse("2026-02-10T00:00:00Z"))
                        .build());

        assertThat(row().state()).isEqualTo("Active");
        assertThat(row().disbursalMonth()).isEqualTo("2026-02");
        assertThat(apps.rows.get("APP-1").disbursed()).isTrue();
    }

    @Test
    void disbursementSuccessRecordsNetAmountAndMonth() {
        projection.onCreated(created());
        projection.onDisbursementSucceeded(
                DisbursementSucceeded.newBuilder()
                        .setMetadata(meta("disbursement.succeeded", loanId.toString()))
                        .setDisbursementId(UUID.randomUUID().toString())
                        .setLoanId(loanId.toString())
                        .setPrincipal(money(50_000_00))
                        .setFee(money(1_000_00))
                        .setGst(money(180_00))
                        .setNetAmount(money(48_820_00))
                        .setDisbursementDate(date("2026-02-10"))
                        .setRailReference("UTR-1")
                        .setSucceededAt(java.time.Instant.parse("2026-02-10T00:00:00Z"))
                        .build());

        assertThat(row().disbursedAmount()).isEqualTo(Money.of(48_820_00, INR));
        assertThat(row().disbursalMonth()).isEqualTo("2026-02");
    }

    @Test
    void accrualsIncreaseOutstandingAndPenalCountsAsBilled() {
        projection.onCreated(created());
        projection.onInterestAccrued(
                LoanInterestAccrued.newBuilder()
                        .setMetadata(meta("loan.interest-accrued", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setBusinessDate(date("2026-03-01"))
                        .setAmount(money(300_00))
                        .setInstalmentNumber(1)
                        .setAccruedAt(java.time.Instant.parse("2026-03-01T00:00:00Z"))
                        .build());
        projection.onPenalAccrued(
                LoanPenalAccrued.newBuilder()
                        .setMetadata(meta("loan.penal-accrued", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setBusinessDate(date("2026-03-01"))
                        .setAmount(money(50_00))
                        .setOverdueBase(money(5_000_00))
                        .setAccruedAt(java.time.Instant.parse("2026-03-01T00:00:00Z"))
                        .build());

        assertThat(row().interestOutstanding()).isEqualTo(Money.of(300_00, INR));
        assertThat(row().penalOutstanding()).isEqualTo(Money.of(50_00, INR));
        assertThat(counters.billed(INR)).isEqualTo(Money.of(50_00, INR));
    }

    @Test
    void allocationDecreasesOutstandingAndCountsAsCollectedThenReversalBacksItOut() {
        projection.onCreated(created());
        // seed some interest/penal to pay down
        projection.onInterestAccrued(
                LoanInterestAccrued.newBuilder()
                        .setMetadata(meta("loan.interest-accrued", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setBusinessDate(date("2026-03-01"))
                        .setAmount(money(1_000_00))
                        .setInstalmentNumber(1)
                        .setAccruedAt(java.time.Instant.parse("2026-03-01T00:00:00Z"))
                        .build());

        LoanRepaymentAllocated alloc =
                LoanRepaymentAllocated.newBuilder()
                        .setMetadata(meta("loan.repayment-allocated", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setRepaymentId(UUID.randomUUID().toString())
                        .setTotalAllocated(money(5_000_00))
                        .setPenal(money(0))
                        .setInterest(money(1_000_00))
                        .setPrincipal(money(4_000_00))
                        .setAdvance(money(0))
                        .setAllocatedAt(java.time.Instant.parse("2026-03-10T00:00:00Z"))
                        .build();
        projection.onRepaymentAllocated(alloc);

        assertThat(row().principalOutstanding()).isEqualTo(Money.of(46_000_00, INR));
        assertThat(row().interestOutstanding()).isEqualTo(Money.zero(INR));
        assertThat(counters.collected(INR)).isEqualTo(Money.of(5_000_00, INR));

        projection.onRepaymentReversed(
                LoanRepaymentReversed.newBuilder()
                        .setMetadata(meta("loan.repayment-reversed", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setRepaymentId(UUID.randomUUID().toString())
                        .setTotalReversed(money(5_000_00))
                        .setPenal(money(0))
                        .setInterest(money(1_000_00))
                        .setPrincipal(money(4_000_00))
                        .setAdvance(money(0))
                        .setReversedAt(java.time.Instant.parse("2026-03-12T00:00:00Z"))
                        .build());

        assertThat(row().principalOutstanding()).isEqualTo(Money.of(50_000_00, INR));
        assertThat(row().interestOutstanding()).isEqualTo(Money.of(1_000_00, INR));
        assertThat(counters.collected(INR)).isEqualTo(Money.zero(INR));
    }

    @Test
    void dpdUpdateSetsBucketAndDelinquentState() {
        projection.onCreated(created());
        projection.onDpdUpdated(dpd(45, "DPD_31_60", true));

        assertThat(row().dpd()).isEqualTo(45);
        assertThat(row().bucket()).isEqualTo(DpdBucketCategory.DPD_31_60);
        assertThat(row().state()).isEqualTo("Delinquent");

        projection.onDpdUpdated(dpd(0, "CURRENT", false));
        assertThat(row().state()).isEqualTo("Active");
    }

    @Test
    void writeOffZeroesBalancesAndPinsTerminalState() {
        projection.onCreated(created());
        projection.onWrittenOff(
                LoanWrittenOff.newBuilder()
                        .setMetadata(meta("loan.written-off", loanId.toString()))
                        .setLoanId(loanId.toString())
                        .setLoanNumber("LN-1")
                        .setRequestId(UUID.randomUUID().toString())
                        .setPrincipal(money(50_000_00))
                        .setInterest(money(0))
                        .setPenal(money(0))
                        .setTotal(money(50_000_00))
                        .setReason("default")
                        .setRequestedBy("admin-a")
                        .setApprovedBy("admin-b")
                        .setWrittenOffAt(java.time.Instant.parse("2026-09-01T00:00:00Z"))
                        .build());

        assertThat(row().state()).isEqualTo("WrittenOff");
        assertThat(row().principalOutstanding()).isEqualTo(Money.zero(INR));
        // A terminal state is not flipped back to Active by a later DPD tick.
        projection.onDpdUpdated(dpd(0, "CURRENT", false));
        assertThat(row().state()).isEqualTo("WrittenOff");
    }

    private LoanDpdUpdated dpd(int days, String bucket, boolean npa) {
        return LoanDpdUpdated.newBuilder()
                .setMetadata(meta("loan.dpd-updated", loanId.toString()))
                .setLoanId(loanId.toString())
                .setLoanNumber("LN-1")
                .setBusinessDate(date("2026-04-01"))
                .setDaysPastDue(days)
                .setBucket(bucket)
                .setOverduePrincipal(money(0))
                .setOverdueInterest(money(0))
                .setOverduePenal(money(0))
                .setOverdueTotal(money(0))
                .setNpa(npa)
                .setUpdatedAt(java.time.Instant.parse("2026-04-01T00:00:00Z"))
                .build();
    }
}
