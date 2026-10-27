package com.lms.reporting.application;

import com.lms.money.Money;
import com.lms.reporting.application.port.out.ApplicationReadRepository;
import com.lms.reporting.application.port.out.AuditLogRepository;
import com.lms.reporting.application.port.out.CollectionCaseRepository;
import com.lms.reporting.application.port.out.CollectionCounterRepository;
import com.lms.reporting.application.port.out.DailySnapshotRepository;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.application.port.out.ReconStatusRepository;
import com.lms.reporting.domain.ApplicationFunnelRow;
import com.lms.reporting.domain.AuditLogRecord;
import com.lms.reporting.domain.AuditQuery;
import com.lms.reporting.domain.CollectionCaseInfo;
import com.lms.reporting.domain.DailySnapshot;
import com.lms.reporting.domain.LoanSnapshotRow;
import com.lms.reporting.domain.ReconStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory fakes for the reporting read-model ports, used by the projection unit tests. */
final class ReportingFakes {

    private ReportingFakes() {}

    static final class InMemoryLoans implements LoanReadRepository {
        final Map<UUID, LoanSnapshotRow> rows = new HashMap<>();
        final Map<UUID, String> appIds = new HashMap<>();

        @Override
        public Optional<LoanSnapshotRow> find(UUID loanId) {
            return Optional.ofNullable(rows.get(loanId));
        }

        @Override
        public Optional<String> findApplicationId(UUID loanId) {
            return Optional.ofNullable(appIds.get(loanId));
        }

        @Override
        public void save(LoanSnapshotRow row, String applicationId) {
            rows.put(row.loanId(), row);
            if (applicationId != null) {
                appIds.put(row.loanId(), applicationId);
            }
        }

        @Override
        public List<LoanSnapshotRow> findAll() {
            return new ArrayList<>(rows.values());
        }
    }

    static final class InMemoryCounters implements CollectionCounterRepository {
        final Map<Currency, long[]> counters = new HashMap<>(); // [billed, collected]

        @Override
        public void addBilled(Money amount) {
            counters.computeIfAbsent(amount.currency(), c -> new long[2])[0] += amount.minorUnits();
        }

        @Override
        public void addCollected(Money amount) {
            counters.computeIfAbsent(amount.currency(), c -> new long[2])[1] += amount.minorUnits();
        }

        @Override
        public Money billed(Currency currency) {
            return Money.of(counters.getOrDefault(currency, new long[2])[0], currency);
        }

        @Override
        public Money collected(Currency currency) {
            return Money.of(counters.getOrDefault(currency, new long[2])[1], currency);
        }
    }

    static final class InMemoryApplications implements ApplicationReadRepository {
        final Map<String, ApplicationFunnelRow> rows = new HashMap<>();

        @Override
        public void merge(String applicationId, String customerId, ApplicationFunnelRow flags) {
            rows.merge(
                    applicationId,
                    new ApplicationFunnelRow(
                            applicationId,
                            flags.submitted(),
                            flags.approved(),
                            flags.offered(),
                            flags.accepted(),
                            flags.disbursed(),
                            flags.rejected(),
                            flags.expired()),
                    (existing, incoming) ->
                            new ApplicationFunnelRow(
                                    applicationId,
                                    existing.submitted() || incoming.submitted(),
                                    existing.approved() || incoming.approved(),
                                    existing.offered() || incoming.offered(),
                                    existing.accepted() || incoming.accepted(),
                                    existing.disbursed() || incoming.disbursed(),
                                    existing.rejected() || incoming.rejected(),
                                    existing.expired() || incoming.expired()));
        }

        @Override
        public List<ApplicationFunnelRow> findAll() {
            return new ArrayList<>(rows.values());
        }
    }

    static final class InMemoryAuditLog implements AuditLogRepository {
        final Map<UUID, AuditLogRecord> rows = new LinkedHashMap<>();

        @Override
        public void insert(AuditLogRecord record) {
            rows.putIfAbsent(record.eventId(), record); // idempotent on event id
        }

        @Override
        public List<AuditLogRecord> query(AuditQuery q) {
            return rows.values().stream()
                    .filter(r -> q.actorId() == null || q.actorId().equals(r.actorId()))
                    .filter(r -> q.action() == null || q.action().equals(r.action()))
                    .sorted(Comparator.comparing(AuditLogRecord::recordedAt).reversed())
                    .limit(q.limit())
                    .toList();
        }
    }

    static final class InMemoryReconStatus implements ReconStatusRepository {
        final Map<UUID, ReconStatus> rows = new LinkedHashMap<>();

        @Override
        public void insert(ReconStatus status) {
            rows.putIfAbsent(status.reconRunId(), status);
        }

        @Override
        public Optional<ReconStatus> latest(String reconType) {
            return rows.values().stream()
                    .filter(r -> reconType.equals(r.reconType()))
                    .max(Comparator.comparing(ReconStatus::runAt));
        }
    }

    static final class InMemoryCases implements CollectionCaseRepository {
        final Map<UUID, CollectionCaseInfo> rows = new LinkedHashMap<>();

        @Override
        public void upsert(CollectionCaseInfo info) {
            rows.merge(
                    info.loanId(),
                    info,
                    (existing, incoming) ->
                            new CollectionCaseInfo(
                                    incoming.loanId(),
                                    incoming.caseId(),
                                    incoming.bucket() != null
                                            ? incoming.bucket()
                                            : existing.bucket(),
                                    incoming.status(),
                                    incoming.openedAt() != null
                                            ? incoming.openedAt()
                                            : existing.openedAt()));
        }

        @Override
        public List<CollectionCaseInfo> findAll() {
            return new ArrayList<>(rows.values());
        }
    }

    static final class InMemorySnapshots implements DailySnapshotRepository {
        final Map<java.time.LocalDate, DailySnapshot> rows = new LinkedHashMap<>();

        @Override
        public void upsert(DailySnapshot snapshot) {
            rows.put(snapshot.businessDate(), snapshot);
        }

        @Override
        public List<DailySnapshot> recent(int limit) {
            return rows.values().stream()
                    .sorted(Comparator.comparing(DailySnapshot::businessDate).reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
