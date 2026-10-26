package com.lms.collections.adapters.out.persistence;

import com.lms.collections.application.port.out.CollectionCaseRepository;
import com.lms.collections.domain.CaseStatus;
import com.lms.collections.domain.CollectionCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for collection cases (no JPA). Optimistic locking on {@code version}. */
@Repository
public class JdbcCollectionCaseRepository implements CollectionCaseRepository {

    private final JdbcClient jdbc;

    public JdbcCollectionCaseRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(CollectionCase c) {
        jdbc.sql(
                        """
                        insert into collection_cases (id, loan_id, loan_number, status, bucket,
                            days_past_due, assigned_agent, promise_date, promise_amount_minor,
                            currency, plan_version, created_at, updated_at, version)
                        values (:id, :loanId, :loanNumber, :status, :bucket, :dpd, :agent,
                            :promiseDate, :promiseAmount, :currency, :planVersion, :createdAt,
                            :updatedAt, :version)
                        """)
                .param("id", c.id())
                .param("loanId", c.loanId())
                .param("loanNumber", c.loanNumber())
                .param("status", c.status().name())
                .param("bucket", c.bucket())
                .param("dpd", c.daysPastDue())
                .param("agent", c.assignedAgent())
                .param("promiseDate", c.promiseDate())
                .param("promiseAmount", c.promiseAmountMinor())
                .param("currency", c.currency())
                .param("planVersion", c.planVersion())
                .param("createdAt", java.sql.Timestamp.from(c.createdAt()))
                .param("updatedAt", java.sql.Timestamp.from(c.updatedAt()))
                .param("version", c.version())
                .update();
    }

    @Override
    public void update(CollectionCase c) {
        int rows =
                jdbc.sql(
                                """
                                update collection_cases set status = :status, bucket = :bucket,
                                    days_past_due = :dpd, assigned_agent = :agent,
                                    promise_date = :promiseDate, promise_amount_minor = :promiseAmount,
                                    currency = :currency, updated_at = :updatedAt,
                                    version = version + 1
                                where id = :id and version = :version
                                """)
                        .param("status", c.status().name())
                        .param("bucket", c.bucket())
                        .param("dpd", c.daysPastDue())
                        .param("agent", c.assignedAgent())
                        .param("promiseDate", c.promiseDate())
                        .param("promiseAmount", c.promiseAmountMinor())
                        .param("currency", c.currency())
                        .param("updatedAt", java.sql.Timestamp.from(c.updatedAt()))
                        .param("id", c.id())
                        .param("version", c.version())
                        .update();
        if (rows == 0) {
            throw new OptimisticLockingException("case " + c.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<CollectionCase> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcCollectionCaseRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<CollectionCase> findByLoanId(String loanId) {
        return jdbc.sql(SELECT + " where loan_id = :loanId")
                .param("loanId", loanId)
                .query(JdbcCollectionCaseRepository::mapRow)
                .optional();
    }

    @Override
    public List<CollectionCase> find(
            Optional<String> bucket, Optional<String> status, Optional<String> agent) {
        return jdbc.sql(
                        SELECT
                                + " where (:bucket is null or bucket = :bucket)"
                                + " and (:status is null or status = :status)"
                                + " and (:agent is null or assigned_agent = :agent)"
                                + " order by days_past_due desc, created_at")
                .param("bucket", bucket.orElse(null))
                .param("status", status.orElse(null))
                .param("agent", agent.orElse(null))
                .query(JdbcCollectionCaseRepository::mapRow)
                .list();
    }

    private static final String SELECT =
            """
            select id, loan_id, loan_number, status, bucket, days_past_due, assigned_agent,
                   promise_date, promise_amount_minor, currency, plan_version, created_at,
                   updated_at, version
            from collection_cases
            """;

    private static CollectionCase mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date promise = rs.getDate("promise_date");
        Long promiseAmount = (Long) rs.getObject("promise_amount_minor");
        return new CollectionCase(
                rs.getObject("id", UUID.class),
                rs.getString("loan_id"),
                rs.getString("loan_number"),
                CaseStatus.valueOf(rs.getString("status")),
                rs.getString("bucket"),
                rs.getInt("days_past_due"),
                rs.getString("assigned_agent"),
                promise == null ? null : promise.toLocalDate(),
                promiseAmount,
                rs.getString("currency"),
                rs.getInt("plan_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }
}
