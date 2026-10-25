package com.lms.repayment.application;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.CallbackDedupStore;
import com.lms.repayment.application.port.out.MandateClient;
import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.application.port.out.PaymentsClient;
import com.lms.repayment.application.port.out.RepaymentEventPublisher;
import com.lms.repayment.application.port.out.RepaymentRepository;
import com.lms.repayment.domain.Mandate;
import com.lms.repayment.domain.Repayment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory fakes for repayment application tests. */
public final class RepaymentFakes {

    private RepaymentFakes() {}

    public static final class FakeRepaymentRepository implements RepaymentRepository {
        final Map<UUID, Repayment> byId = new HashMap<>();
        final Map<String, Repayment> byRef = new HashMap<>();

        @Override
        public void save(Repayment r) {
            byId.put(r.id(), r);
            byRef.put(r.providerReference(), r);
        }

        @Override
        public Optional<Repayment> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Repayment> findByProviderReference(String ref) {
            return Optional.ofNullable(byRef.get(ref));
        }

        @Override
        public List<Repayment> findByLoanId(UUID loanId) {
            return byId.values().stream().filter(r -> r.loanId().equals(loanId)).toList();
        }
    }

    public static final class FakeMandateRepository implements MandateRepository {
        final Map<UUID, Mandate> byId = new HashMap<>();

        @Override
        public void save(Mandate m) {
            byId.put(m.id(), m);
        }

        @Override
        public Optional<Mandate> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Mandate> findActiveByLoanId(UUID loanId) {
            return byId.values().stream()
                    .filter(m -> m.loanId().equals(loanId) && m.status().isActive())
                    .findFirst();
        }
    }

    public static final class FakeDedup implements CallbackDedupStore {
        final Set<String> seen = new HashSet<>();

        @Override
        public boolean firstSeen(String providerReference, String status) {
            return seen.add(providerReference + "|" + status);
        }
    }

    public static final class RecordingPublisher implements RepaymentEventPublisher {
        public final List<String> events = new ArrayList<>();

        @Override
        public void repaymentReceived(Repayment r) {
            events.add("repayment.received:" + r.providerReference());
        }

        @Override
        public void repaymentReversed(Repayment r, String reason) {
            events.add("repayment.reversed:" + r.providerReference() + ":" + reason);
        }

        @Override
        public void mandateRegistered(Mandate m) {
            events.add("mandate.registered:" + m.id());
        }

        @Override
        public void mandateDebitFailed(
                UUID loanId,
                String mandateId,
                Money amount,
                LocalDate dueDate,
                String failureCode,
                LocalDate rePresentOn) {
            events.add("mandate.debit-failed:" + loanId + ":" + rePresentOn);
        }
    }

    public static final class FakeMandateClient implements MandateClient {
        final List<String> debits = new ArrayList<>();

        @Override
        public String register(UUID loanId, String customerId, String callbackUrl) {
            return "MND-" + loanId;
        }

        @Override
        public void debit(
                String providerMandateId,
                UUID loanId,
                Money amount,
                String idempotencyKey,
                String callbackUrl) {
            debits.add(idempotencyKey);
        }

        @Override
        public void cancel(String providerMandateId) {}
    }

    public static final class FakePaymentsClient implements PaymentsClient {
        @Override
        public PaymentLink createLink(UUID loanId, Money amount, String callbackUrl) {
            return new PaymentLink("PL-" + loanId, "https://pay.local/PL-" + loanId);
        }
    }
}
