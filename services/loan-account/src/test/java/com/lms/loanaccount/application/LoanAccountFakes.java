package com.lms.loanaccount.application;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.application.port.out.LoanEventPublisher;
import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.application.port.out.ProductCatalog;
import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.AllocationResult;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.loanaccount.domain.LoanState;
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
    }

    static final class FakeAccrualRepository implements AccrualRepository {
        private record Key(UUID loanId, LocalDate date) {}

        final Map<Key, Money> accruals = new LinkedHashMap<>();

        @Override
        public boolean recordInterestAccrual(
                UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
            Key key = new Key(loanId, businessDate);
            if (accruals.containsKey(key)) {
                return false;
            }
            accruals.put(key, amount);
            return true;
        }

        @Override
        public Money totalInterestAccrued(UUID loanId) {
            return accruals.entrySet().stream()
                    .filter(e -> e.getKey().loanId().equals(loanId))
                    .map(Map.Entry::getValue)
                    .reduce(Money.zero(INR), Money::add);
        }
    }

    static final class FakeAllocationRepository implements AllocationRepository {
        final List<Allocation> saved = new ArrayList<>();
        String lastRepaymentId;

        @Override
        public void saveAll(UUID loanId, String repaymentId, List<Allocation> allocations) {
            this.lastRepaymentId = repaymentId;
            this.saved.addAll(allocations);
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
        public void loanClosed(Loan loan, String reason, UUID causationEventId) {
            events.add("loan.closed:" + loan.loanNumber() + ":" + reason);
        }

        @Override
        public void loanCancelled(Loan loan, String reason, UUID causationEventId) {
            events.add("loan.cancelled:" + loan.loanNumber() + ":" + reason);
        }
    }
}
