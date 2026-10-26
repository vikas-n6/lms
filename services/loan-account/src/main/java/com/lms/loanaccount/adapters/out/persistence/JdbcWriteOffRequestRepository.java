package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.port.out.WriteOffRequestRepository;
import com.lms.loanaccount.domain.WriteOffRequest;
import com.lms.loanaccount.domain.WriteOffRequestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for write-off requests (no JPA). Optimistic locking on {@code version}.
 */
@Repository
public class JdbcWriteOffRequestRepository implements WriteOffRequestRepository {

    private final JdbcClient jdbc;

    public JdbcWriteOffRequestRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(WriteOffRequest r) {
        jdbc.sql(
                        """
                        insert into write_off_requests (id, loan_id, loan_number, reason,
                            requested_by, status, decided_by, decision_note, dpd_at_request,
                            created_at, updated_at, version)
                        values (:id, :loanId, :loanNumber, :reason, :requestedBy, :status,
                            :decidedBy, :note, :dpd, :createdAt, :updatedAt, :version)
                        """)
                .param("id", r.id())
                .param("loanId", r.loanId())
                .param("loanNumber", r.loanNumber())
                .param("reason", r.reason())
                .param("requestedBy", r.requestedBy())
                .param("status", r.status().name())
                .param("decidedBy", r.decidedBy())
                .param("note", r.decisionNote())
                .param("dpd", r.dpdAtRequest())
                .param("createdAt", java.sql.Timestamp.from(r.createdAt()))
                .param("updatedAt", java.sql.Timestamp.from(r.updatedAt()))
                .param("version", r.version())
                .update();
    }

    @Override
    public void update(WriteOffRequest r) {
        int rows =
                jdbc.sql(
                                """
                                update write_off_requests set status = :status,
                                    decided_by = :decidedBy, decision_note = :note,
                                    updated_at = :updatedAt, version = version + 1
                                where id = :id and version = :version
                                """)
                        .param("status", r.status().name())
                        .param("decidedBy", r.decidedBy())
                        .param("note", r.decisionNote())
                        .param("updatedAt", java.sql.Timestamp.from(r.updatedAt()))
                        .param("id", r.id())
                        .param("version", r.version())
                        .update();
        if (rows == 0) {
            throw new OptimisticLockingException(
                    "write-off request " + r.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<WriteOffRequest> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcWriteOffRequestRepository::mapRow)
                .optional();
    }

    @Override
    public List<WriteOffRequest> findByLoanId(UUID loanId) {
        return jdbc.sql(SELECT + " where loan_id = :loanId order by created_at desc")
                .param("loanId", loanId)
                .query(JdbcWriteOffRequestRepository::mapRow)
                .list();
    }

    @Override
    public List<WriteOffRequest> findAll(Optional<String> status) {
        return jdbc.sql(
                        SELECT
                                + " where (:status is null or status = :status)"
                                + " order by created_at desc")
                .param("status", status.orElse(null))
                .query(JdbcWriteOffRequestRepository::mapRow)
                .list();
    }

    private static final String SELECT =
            """
            select id, loan_id, loan_number, reason, requested_by, status, decided_by,
                   decision_note, dpd_at_request, created_at, updated_at, version
            from write_off_requests
            """;

    private static WriteOffRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WriteOffRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("loan_id", UUID.class),
                rs.getString("loan_number"),
                rs.getString("reason"),
                rs.getString("requested_by"),
                WriteOffRequestStatus.valueOf(rs.getString("status")),
                rs.getString("decided_by"),
                rs.getString("decision_note"),
                rs.getInt("dpd_at_request"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }
}
