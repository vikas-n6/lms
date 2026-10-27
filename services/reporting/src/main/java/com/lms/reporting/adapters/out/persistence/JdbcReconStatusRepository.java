package com.lms.reporting.adapters.out.persistence;

import com.lms.reporting.application.port.out.ReconStatusRepository;
import com.lms.reporting.domain.ReconStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only reconciliation-status store; {@code insert} is idempotent on the run id. */
@Repository
public class JdbcReconStatusRepository implements ReconStatusRepository {

    private static final String SELECT =
            """
            select recon_run_id, recon_type, currency, balanced, diff_minor, matched,
                   missing_in_ledger, missing_in_bank, amount_mismatch, duplicate, run_at
            from recon_status
            """;

    private final JdbcClient jdbc;

    public JdbcReconStatusRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void insert(ReconStatus s) {
        jdbc.sql(
                        """
                        insert into recon_status (recon_run_id, recon_type, currency, balanced,
                            diff_minor, matched, missing_in_ledger, missing_in_bank, amount_mismatch,
                            duplicate, run_at, stored_at)
                        values (:id, :type, :ccy, :balanced, :diff, :matched, :ml, :mb, :am, :dup,
                            :runAt, now())
                        on conflict (recon_run_id) do nothing
                        """)
                .param("id", s.reconRunId())
                .param("type", s.reconType())
                .param("ccy", s.currency())
                .param("balanced", s.balanced())
                .param("diff", s.diffMinor())
                .param("matched", s.matched())
                .param("ml", s.missingInLedger())
                .param("mb", s.missingInBank())
                .param("am", s.amountMismatch())
                .param("dup", s.duplicate())
                .param("runAt", java.sql.Timestamp.from(s.runAt()))
                .update();
    }

    @Override
    public Optional<ReconStatus> latest(String reconType) {
        return jdbc.sql(SELECT + " where recon_type = :type order by run_at desc limit 1")
                .param("type", reconType)
                .query(JdbcReconStatusRepository::mapRow)
                .optional();
    }

    private static ReconStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReconStatus(
                rs.getObject("recon_run_id", java.util.UUID.class),
                rs.getString("recon_type"),
                rs.getString("currency"),
                rs.getBoolean("balanced"),
                rs.getLong("diff_minor"),
                rs.getInt("matched"),
                rs.getInt("missing_in_ledger"),
                rs.getInt("missing_in_bank"),
                rs.getInt("amount_mismatch"),
                rs.getInt("duplicate"),
                rs.getTimestamp("run_at").toInstant());
    }
}
