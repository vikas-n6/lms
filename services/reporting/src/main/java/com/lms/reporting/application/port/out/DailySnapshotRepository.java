package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.DailySnapshot;
import java.util.List;

/** Store for the daily portfolio snapshot series. {@code upsert} replaces the row for its date. */
public interface DailySnapshotRepository {

    void upsert(DailySnapshot snapshot);

    /** Most recent snapshots first, up to {@code limit}. */
    List<DailySnapshot> recent(int limit);
}
