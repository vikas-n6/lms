package com.lms.reporting.adapters.out.persistence;

import com.lms.reporting.application.port.out.DailySnapshotRepository;
import com.lms.reporting.domain.DailySnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC store for the daily snapshot series. {@code upsert} replaces the row for its date.
 */
@Repository
public class JdbcDailySnapshotRepository implements DailySnapshotRepository {

    private final JdbcClient jdbc;

    public JdbcDailySnapshotRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void upsert(DailySnapshot s) {
        jdbc.sql(
                        """
                        insert into daily_snapshot (business_date, currency, total_loans,
                            active_loans, outstanding_principal_minor, disbursed_minor,
                            npa_ratio_bps, dpd_current, dpd_1_30, dpd_31_60, dpd_61_90, dpd_npa,
                            created_at)
                        values (:date, :ccy, :total, :active, :principal, :disbursed, :npa,
                            :current, :b1, :b2, :b3, :npaCount, :createdAt)
                        on conflict (business_date) do update set
                            currency = excluded.currency,
                            total_loans = excluded.total_loans,
                            active_loans = excluded.active_loans,
                            outstanding_principal_minor = excluded.outstanding_principal_minor,
                            disbursed_minor = excluded.disbursed_minor,
                            npa_ratio_bps = excluded.npa_ratio_bps,
                            dpd_current = excluded.dpd_current,
                            dpd_1_30 = excluded.dpd_1_30,
                            dpd_31_60 = excluded.dpd_31_60,
                            dpd_61_90 = excluded.dpd_61_90,
                            dpd_npa = excluded.dpd_npa,
                            created_at = excluded.created_at
                        """)
                .param("date", s.businessDate())
                .param("ccy", s.currency())
                .param("total", s.totalLoans())
                .param("active", s.activeLoans())
                .param("principal", s.outstandingPrincipalMinor())
                .param("disbursed", s.disbursedMinor())
                .param("npa", s.npaRatioBps())
                .param("current", s.dpdCurrent())
                .param("b1", s.dpd1To30())
                .param("b2", s.dpd31To60())
                .param("b3", s.dpd61To90())
                .param("npaCount", s.dpdNpa())
                .param("createdAt", java.sql.Timestamp.from(s.createdAt()))
                .update();
    }

    @Override
    public List<DailySnapshot> recent(int limit) {
        return jdbc.sql(
                        """
                        select business_date, currency, total_loans, active_loans,
                               outstanding_principal_minor, disbursed_minor, npa_ratio_bps,
                               dpd_current, dpd_1_30, dpd_31_60, dpd_61_90, dpd_npa, created_at
                        from daily_snapshot
                        order by business_date desc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(JdbcDailySnapshotRepository::mapRow)
                .list();
    }

    private static DailySnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DailySnapshot(
                rs.getObject("business_date", java.time.LocalDate.class),
                rs.getString("currency"),
                rs.getInt("total_loans"),
                rs.getInt("active_loans"),
                rs.getLong("outstanding_principal_minor"),
                rs.getLong("disbursed_minor"),
                rs.getLong("npa_ratio_bps"),
                rs.getInt("dpd_current"),
                rs.getInt("dpd_1_30"),
                rs.getInt("dpd_31_60"),
                rs.getInt("dpd_61_90"),
                rs.getInt("dpd_npa"),
                rs.getTimestamp("created_at").toInstant());
    }
}
