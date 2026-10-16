package com.lms.disbursement.adapters.out.persistence;

import com.lms.disbursement.application.port.out.PayoutAttemptRepository;
import com.lms.disbursement.domain.PayoutAttempt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for payout attempts. {@code save} upserts on the attempt id. */
@Repository
public class JdbcPayoutAttemptRepository implements PayoutAttemptRepository {

    private static final String SELECT =
            """
            select id, disbursement_id, attempt_number, idempotency_key, status, failure_code,
                   started_at, finished_at
            from payout_attempts
            """;

    private final JdbcClient jdbc;

    public JdbcPayoutAttemptRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(PayoutAttempt a) {
        jdbc.sql(
                        """
                        insert into payout_attempts (id, disbursement_id, attempt_number,
                            idempotency_key, status, failure_code, started_at, finished_at)
                        values (:id, :disbursementId, :attemptNumber, :idempotencyKey, :status,
                            :failureCode, :startedAt, :finishedAt)
                        on conflict (id) do update set
                            status = excluded.status,
                            failure_code = excluded.failure_code,
                            finished_at = excluded.finished_at,
                            updated_at = now(),
                            version = payout_attempts.version + 1
                        """)
                .param("id", a.id())
                .param("disbursementId", a.disbursementId())
                .param("attemptNumber", a.attemptNumber())
                .param("idempotencyKey", a.idempotencyKey())
                .param("status", a.status().name())
                .param("failureCode", a.failureCode())
                .param("startedAt", a.startedAt())
                .param("finishedAt", a.finishedAt())
                .update();
    }

    @Override
    public Optional<PayoutAttempt> find(UUID disbursementId, int attemptNumber) {
        return jdbc.sql(SELECT + " where disbursement_id = :d and attempt_number = :n")
                .param("d", disbursementId)
                .param("n", attemptNumber)
                .query(JdbcPayoutAttemptRepository::mapRow)
                .optional();
    }

    @Override
    public List<PayoutAttempt> findByDisbursementId(UUID disbursementId) {
        return jdbc.sql(SELECT + " where disbursement_id = :d order by attempt_number")
                .param("d", disbursementId)
                .query(JdbcPayoutAttemptRepository::mapRow)
                .list();
    }

    private static PayoutAttempt mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PayoutAttempt(
                rs.getObject("id", UUID.class),
                rs.getObject("disbursement_id", UUID.class),
                rs.getInt("attempt_number"),
                rs.getString("idempotency_key"),
                PayoutAttempt.Status.valueOf(rs.getString("status")),
                rs.getString("failure_code"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null
                        ? null
                        : rs.getTimestamp("finished_at").toInstant());
    }
}
