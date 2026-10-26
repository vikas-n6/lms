package com.lms.collections.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.application.port.out.CollectionsEventPublisher;
import com.lms.collections.application.port.out.DunningActionRepository;
import com.lms.collections.application.port.out.DunningPlanProvider;
import com.lms.collections.application.port.out.DunningWorkflowClient;
import com.lms.collections.domain.CollectionCase;
import com.lms.collections.domain.DunningAction;
import com.lms.collections.domain.DunningActionType;
import com.lms.collections.domain.DunningPlan;
import com.lms.collections.domain.DunningStep;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The collections orchestration (phase prompt task 4): a case opens at DPD 1 or more and starts the
 * workflow, each DPD tick is signalled, DPD back to zero signals payment-cleared, a loan write-off
 * signals the workflow, and admin actions route to the workflow. Pure in-memory fakes, no Docker.
 */
class CollectionsServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-20T06:00:00Z"), ZoneOffset.UTC);

    private FakeCaseRepository cases;
    private FakeWorkflowClient workflow;
    private FakePublisher publisher;
    private CollectionsService service;
    private CaseQueryService query;

    @BeforeEach
    void setUp() {
        cases = new FakeCaseRepository();
        workflow = new FakeWorkflowClient();
        publisher = new FakePublisher();
        DunningPlanProvider plans =
                () ->
                        new DunningPlan(
                                1,
                                List.of(
                                        new DunningStep(
                                                "DPD_1_SMS",
                                                1,
                                                DunningActionType.SMS,
                                                "SMS",
                                                "t")));
        service = new CollectionsService(cases, workflow, publisher, plans, CLOCK);
        query = new CaseQueryService(cases, new FakeActionRepository());
    }

    @Test
    void opensCaseAtDpd1AndSignalsTheTick() {
        service.onDpdUpdated("loan-1", "LN-1", 1, "DPD_1_30", LocalDate.of(2026, 11, 20));

        assertThat(cases.byLoan).containsKey("loan-1");
        assertThat(publisher.opened).containsExactly("loan-1");
        assertThat(workflow.started).containsExactly("loan-1");
        assertThat(workflow.ticks).containsExactly("loan-1:1");
    }

    @Test
    void doesNotReopenAnExistingCaseButStillSignalsTicks() {
        service.onDpdUpdated("loan-1", "LN-1", 1, "DPD_1_30", LocalDate.of(2026, 11, 20));
        service.onDpdUpdated("loan-1", "LN-1", 5, "DPD_1_30", LocalDate.of(2026, 11, 24));

        assertThat(workflow.started).containsExactly("loan-1"); // started once
        assertThat(workflow.ticks).containsExactly("loan-1:1", "loan-1:5");
        assertThat(cases.byLoan.get("loan-1").daysPastDue()).isEqualTo(5);
    }

    @Test
    void dpdZeroSignalsPaymentClearedOnlyWhenACaseExists() {
        // No case yet: a zero tick does nothing.
        service.onDpdUpdated("loan-x", "LN-X", 0, "CURRENT", LocalDate.of(2026, 11, 20));
        assertThat(workflow.cleared).isEmpty();

        service.onDpdUpdated("loan-1", "LN-1", 2, "DPD_1_30", LocalDate.of(2026, 11, 20));
        service.onDpdUpdated("loan-1", "LN-1", 0, "CURRENT", LocalDate.of(2026, 11, 25));
        assertThat(workflow.cleared).containsExactly("loan-1");
    }

    @Test
    void loanWrittenOffSignalsTheWorkflow() {
        service.onDpdUpdated("loan-1", "LN-1", 200, "NPA", LocalDate.of(2027, 6, 1));
        service.onLoanWrittenOff("loan-1");
        assertThat(workflow.writtenOff).containsExactly("loan-1");
    }

    @Test
    void adminActionsRouteToTheWorkflow() {
        service.onDpdUpdated("loan-1", "LN-1", 20, "DPD_1_30", LocalDate.of(2026, 12, 5));
        UUID caseId = cases.byLoan.get("loan-1").id();

        service.assignAgent(caseId, "agent-3");
        service.recordPromise(caseId, LocalDate.of(2026, 12, 20), 474_000L, "INR");
        service.escalate(caseId, "unreachable");
        service.resolve(caseId, "settled offline");

        assertThat(workflow.assigns).containsExactly("loan-1:agent-3");
        assertThat(workflow.promises).containsExactly("loan-1:2026-12-20");
        assertThat(workflow.escalates).containsExactly("loan-1:unreachable");
        assertThat(workflow.cleared).contains("loan-1");
        assertThat(query.detail(caseId)).isPresent();
    }

    // --- Fakes ---------------------------------------------------------------------------------

    private static final class FakeCaseRepository implements CollectionCaseRepository {
        final Map<String, CollectionCase> byLoan = new LinkedHashMap<>();
        final Map<UUID, CollectionCase> byId = new LinkedHashMap<>();

        @Override
        public void insert(CollectionCase c) {
            byLoan.put(c.loanId(), c);
            byId.put(c.id(), c);
        }

        @Override
        public void update(CollectionCase c) {
            byLoan.put(c.loanId(), c);
            byId.put(c.id(), c);
        }

        @Override
        public Optional<CollectionCase> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<CollectionCase> findByLoanId(String loanId) {
            return Optional.ofNullable(byLoan.get(loanId));
        }

        @Override
        public List<CollectionCase> find(
                Optional<String> bucket, Optional<String> status, Optional<String> agent) {
            return List.copyOf(byLoan.values());
        }
    }

    private static final class FakeActionRepository implements DunningActionRepository {
        @Override
        public boolean recordIfAbsent(DunningAction action) {
            return true;
        }

        @Override
        public List<DunningAction> findByCaseId(UUID caseId) {
            return List.of();
        }
    }

    private static final class FakeWorkflowClient implements DunningWorkflowClient {
        final List<String> started = new ArrayList<>();
        final List<String> ticks = new ArrayList<>();
        final List<String> cleared = new ArrayList<>();
        final List<String> promises = new ArrayList<>();
        final List<String> escalates = new ArrayList<>();
        final List<String> assigns = new ArrayList<>();
        final List<String> writtenOff = new ArrayList<>();

        @Override
        public void start(CollectionCase c, DunningPlan plan) {
            started.add(c.loanId());
        }

        @Override
        public void signalDpdTick(
                String loanId, int daysPastDue, String bucket, LocalDate businessDate) {
            ticks.add(loanId + ":" + daysPastDue);
        }

        @Override
        public void signalPaymentReceived(String loanId, boolean overdueCleared) {
            if (overdueCleared) {
                cleared.add(loanId);
            }
        }

        @Override
        public void signalPromiseToPay(
                String loanId, LocalDate promiseDate, long amountMinor, String currency) {
            promises.add(loanId + ":" + promiseDate);
        }

        @Override
        public void signalEscalate(String loanId, String reason) {
            escalates.add(loanId + ":" + reason);
        }

        @Override
        public void signalAssignAgent(String loanId, String agent) {
            assigns.add(loanId + ":" + agent);
        }

        @Override
        public void signalWrittenOff(String loanId) {
            writtenOff.add(loanId);
        }
    }

    private static final class FakePublisher implements CollectionsEventPublisher {
        final List<String> opened = new ArrayList<>();

        @Override
        public void caseOpened(CollectionCase c) {
            opened.add(c.loanId());
        }

        @Override
        public void reminderDue(
                String caseId, String loanId, String loanNumber, DunningAction action) {}

        @Override
        public void escalated(
                String caseId,
                String loanId,
                String loanNumber,
                String status,
                String stepId,
                String reason) {}

        @Override
        public void caseClosed(
                String caseId, String loanId, String loanNumber, String status, String reason) {}
    }
}
