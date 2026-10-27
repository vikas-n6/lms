package com.lms.reporting.application;

import com.lms.config.BusinessCalendar;
import com.lms.reporting.application.port.out.DailySnapshotRepository;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.domain.DailySnapshot;
import com.lms.reporting.domain.PortfolioMetrics;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Captures a daily portfolio snapshot keyed by the business date (one row per date, upserted so a
 * re-run is idempotent). The business date comes from the shared {@link BusinessCalendar}, never
 * wall-clock, so the series follows simulated business days (docs/architecture.md, CLAUDE.md).
 */
public class SnapshotService {

    private final LoanReadRepository loans;
    private final DailySnapshotRepository snapshots;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final Currency currency;

    public SnapshotService(
            LoanReadRepository loans,
            DailySnapshotRepository snapshots,
            BusinessCalendar calendar,
            Clock clock,
            Currency currency) {
        this.loans = Objects.requireNonNull(loans);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.calendar = Objects.requireNonNull(calendar);
        this.clock = Objects.requireNonNull(clock);
        this.currency = Objects.requireNonNull(currency);
    }

    /** Compute and persist the snapshot for the current business date; returns the row written. */
    public DailySnapshot captureDaily() {
        PortfolioMetrics metrics = PortfolioMetrics.from(loans.findAll(), currency);
        DailySnapshot snapshot = DailySnapshot.from(calendar.today(), metrics, clock.instant());
        snapshots.upsert(snapshot);
        return snapshot;
    }

    public List<DailySnapshot> recent(int limit) {
        return snapshots.recent(limit <= 0 ? 30 : Math.min(limit, 365));
    }
}
