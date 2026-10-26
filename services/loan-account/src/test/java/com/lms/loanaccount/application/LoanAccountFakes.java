package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.DpdSnapshotRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.application.port.out.WriteOffRequestRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.DpdSnapshot;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
import com.lms.loanaccount.domain.WriteOffRequest;
import com.lms.money.Money;
import com.lms.money.Rate;
import com.lms.schedule.FixedTenorReducingBalanceProduct;
import com.lms.schedule.Product;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory fakes for loan-account application tests. */
final class LoanAccountFakes {

    static final Currency INR = Currency.getInstance("INR");

    private LoanAccountFakes() {}

    static Product inrProduct() {
        return new FixedTenorReducingBalanceProduct(
                "MICRO-V1",
                1,
                INR,
                Money.of(500_000L, INR),
                Money.of(5_000_000L, INR),
                Money.of(50_000L, INR),
                List.of(3, 6, 9, 12),
                Rate.percent(24),
                Rate.percent(2),
                Rate.percent(18),
                Rate.percent(36),
                3,
                Rate.zero());
    }

    static final class FakeProductCatalog implements ProductCatalog {
        private final Product product = inrProduct();

        @Override
        public Product current() {
            return product;
        }

        @Override
        public Product byVersion(String productId, int version) {
            return product;
        }
    }

    static final class FakeLoanRepository implements LoanRepository {
        final Map<UUID, Loan> byId = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong(0);

        @Override
        public Optional<Loan> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Loan> findByApplicationId(String applicationId) {
            return byId.values().stream()
                    .filter(l -> l.applicationId().equals(applicationId))
                    .findFirst();
        }

        @Override
        public long nextLoanSequence() {
            return sequence.incrementAndGet();
        }

        @Override
        public void insert(Loan loan) {
            if (byId.containsKey(loan.id())) {
                throw new IllegalStateException("duplicate loan id");
            }
            byId.put(loan.id(), loan);
        }

        @Override
        public void update(Loan loan) {
            byId.put(loan.id(), loan);
        }

        @Override
        public List<Loan> findAll(Optional<String> stateCode, Optional<String> customerId) {
            return byId.values().stream()
                    .filter(l -> stateCode.map(s -> l.state().code().equals(s)).orElse(true))
                    .filter(l -> customerId.map(c -> l.customerId().equals(c)).orElse(true))
                    .toList();
        }
    }

    static final class FakeScheduleRepository implements ScheduleRepository {
        final Map<UUID, List<LoanScheduleLine>> byLoan = new LinkedHashMap<>();

        @Override
        public List<LoanScheduleLine> findByLoanId(UUID loanId) {
            return byLoan.getOrDefault(loanId, List.of());
        }

        @Override
        public void replaceSchedule(UUID loanId, List<LoanScheduleLine> lines) {
            byLoan.put(loanId, new ArrayList<>(lines));
        }

        @Override
        public void saveAll(List<LoanScheduleLine> lines) {
            if (lines.isEmpty()) {
                return;
            }
            UUID loanId = lines.get(0).loanId();
            List<LoanScheduleLine> current =
                    new ArrayList<>(byLoan.getOrDefault(loanId, List.of()));
            for (LoanScheduleLine line : lines) {
                for (int i = 0; i < current.size(); i++) {
                    if (current.get(i).instalmentNumber() == line.instalmentNumber()) {
                        current.set(i, line);
                    }
                }
            }
            byLoan.put(loanId, current);
        }
    }

    static final class FakeStateHistoryRepository implements StateHistoryRepository {
        record Entry(
                UUID loanId,
                LoanState from,
                LoanState to,
                String cause,
                UUID eventId,
                boolean legal) {}

        final List<Entry> entries = new ArrayList<>();

        @Override
        public void append(
                UUID loanId,
                LoanState from,
                LoanState to,
                String cause,
                UUID eventId,
                boolean legal) {
            entries.add(new Entry(loanId, from, to, cause, eventId, legal));
        }

        @Override
        public List<StateHistoryRepository.Entry> findByLoanId(UUID loanId) {
            return entries.stream()
                    .filter(e -> e.loanId().equals(loanId))
                    .map(
                            e ->
                                    new StateHistoryRepository.Entry(
                                            e.from() == null ? null : e.from().code(),
                                            e.to().code(),
                                            e.cause(),
                                            e.legal(),
                                            java.time.Instant.EPOCH))
                    .toList();
        }
    }

    static final class FakeAccrualRepository implements AccrualRepository {
        private record Key(UUID loanId, LocalDate date, String kind) {}

        final Map<Key, Money> accruals = new LinkedHashMap<>();

        private boolean record(UUID loanId, LocalDate date, String kind, Money amount) {
            Key key = new Key(loanId, date, kind);
            if (accruals.containsKey(key)) {
                return false;
            }
            accruals.put(key, amount);
            return true;
        }

        @Override
        public boolean recordInterestAccrual(
                UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
            return record(loanId, businessDate, "INTEREST", amount);
        }

        @Override
        public boolean recordPenalAccrual(
                UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
            return record(loanId, businessDate, "PENAL", amount);
        }

        @Override
        public boolean recordDueSoonSent(
                UUID loanId, LocalDate businessDate, int instalmentNumber) {
            return record(loanId, businessDate, "DUE_SOON", Money.zero(INR));
        }

        @Override
        public Money totalInterestAccrued(UUID loanId) {
            return total(loanId, "INTEREST");
        }

        @Override
        public Money totalPenalAccrued(UUID loanId) {
            return total(loanId, "PENAL");
        }

        private Money total(UUID loanId, String kind) {
            return accruals.entrySet().stream()
                    .filter(
                            e ->
                                    e.getKey().loanId().equals(loanId)
                                            && e.getKey().kind().equals(kind))
                    .map(Map.Entry::getValue)
                    .reduce(Money.zero(INR), Money::add);
        }
    }

    static final class FakeDpdSnapshotRepository implements DpdSnapshotRepository {
        final Map<UUID, DpdSnapshot> byLoan = new LinkedHashMap<>();

        @Override
        public Optional<DpdSnapshot> find(UUID loanId) {
            return Optional.ofNullable(byLoan.get(loanId));
        }

        @Override
        public void upsert(DpdSnapshot snapshot) {
            byLoan.put(snapshot.loanId(), snapshot);
        }
    }

    static final class FakeWriteOffRequestRepository implements WriteOffRequestRepository {
        final Map<UUID, WriteOffRequest> byId = new LinkedHashMap<>();

        @Override
        public void insert(WriteOffRequest request) {
            byId.put(request.id(), request);
        }

        @Override
        public void update(WriteOffRequest request) {
            byId.put(request.id(), request);
        }

        @Override
        public Optional<WriteOffRequest> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<WriteOffRequest> findByLoanId(UUID loanId) {
            return byId.values().stream().filter(r -> r.loanId().equals(loanId)).toList();
        }

        @Override
        public List<WriteOffRequest> findAll(Optional<String> status) {
            return byId.values().stream()
                    .filter(r -> status.map(s -> r.status().name().equals(s)).orElse(true))
                    .toList();
        }
    }

    static final class FakeAllocationRepository implements AllocationRepository {
        final List<Allocation> saved = new ArrayList<>();
        final Map<String, List<Allocation>> byRepayment = new LinkedHashMap<>();
        final List<Allocation> reversed = new ArrayList<>();
        String lastRepaymentId;
        String lastReversalOf;

        @Override
        public void saveAll(UUID loanId, String repaymentId, List<Allocation> allocations) {
            this.lastRepaymentId = repaymentId;
            this.saved.addAll(allocations);
            byRepayment.computeIfAbsent(repaymentId, k -> new ArrayList<>()).addAll(allocations);
        }

        @Override
        public List<Allocation> findByRepaymentId(UUID loanId, String repaymentId) {
            return List.copyOf(byRepayment.getOrDefault(repaymentId, List.of()));
        }

        @Override
        public void saveReversal(
                UUID loanId,
                String reversalRepaymentId,
                String reversedRepaymentId,
                List<Allocation> allocations) {
            this.lastReversalOf = reversedRepaymentId;
            this.reversed.addAll(allocations);
        }
    }

    static final class RecordingPublisher implements LoanEventPublisher {
        final List<String> events = new ArrayList<>();
        AllocationResult lastAllocation;

        @Override
        public void loanCreated(Loan loan, UUID causationEventId) {
            events.add("loan.created:" + loan.loanNumber());
        }

        @Override
        public void loanActivated(
                Loan loan, LocalDate firstDueDate, LocalDate maturityDate, UUID causationEventId) {
            events.add("loan.activated:" + loan.loanNumber());
        }

        @Override
        public void interestAccrued(
                UUID loanId,
                String loanNumber,
                LocalDate businessDate,
                int instalmentNumber,
                Money amount) {
            events.add("loan.interest-accrued:" + businessDate + ":" + amount.minorUnits());
        }

        @Override
        public void penalAccrued(
                UUID loanId,
                String loanNumber,
                LocalDate businessDate,
                Money amount,
                Money overdueBase) {
            events.add("loan.penal-accrued:" + businessDate + ":" + amount.minorUnits());
        }

        @Override
        public void dpdUpdated(
                UUID loanId,
                String loanNumber,
                LocalDate businessDate,
                com.lms.loanaccount.domain.DelinquencySnapshot snapshot) {
            events.add("loan.dpd-updated:" + businessDate + ":" + snapshot.daysPastDue());
        }

        @Override
        public void installmentDueSoon(
                UUID loanId,
                String loanNumber,
                int instalmentNumber,
                LocalDate dueDate,
                Money amountDue) {
            events.add("loan.installment-due-soon:" + dueDate + ":" + instalmentNumber);
        }

        @Override
        public void writtenOff(
                Loan loan,
                String requestId,
                Money principal,
                Money interest,
                Money penal,
                String reason,
                String requestedBy,
                String approvedBy,
                UUID causationEventId) {
            events.add("loan.written-off:" + loan.loanNumber() + ":" + requestId);
        }

        @Override
        public void repaymentAllocated(
                UUID loanId,
                String loanNumber,
                String repaymentId,
                AllocationResult result,
                UUID causationEventId) {
            this.lastAllocation = result;
            events.add("loan.repayment-allocated:" + repaymentId);
        }

        @Override
        public void repaymentReversed(
                UUID loanId,
                String loanNumber,
                String repaymentId,
                Money penal,
                Money interest,
                Money principal,
                UUID causationEventId) {
            events.add("loan.repayment-reversed:" + repaymentId);
        }

        @Override
        public void loanForeclosed(
                Loan loan, String repaymentId, Money foreclosureAmount, UUID causationEventId) {
            events.add(
                    "loan.foreclosed:" + loan.loanNumber() + ":" + foreclosureAmount.minorUnits());
        }

        @Override
        public void loanClosed(Loan loan, String reason, UUID causationEventId) {
            events.add("loan.closed:" + loan.loanNumber() + ":" + reason);
        }

        @Override
        public void loanCancelled(Loan loan, String reason, UUID causationEventId) {
            events.add("loan.cancelled:" + loan.loanNumber() + ":" + reason);
        }
    }
}
