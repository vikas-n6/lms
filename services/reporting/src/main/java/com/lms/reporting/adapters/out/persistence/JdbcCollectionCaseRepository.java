package com.lms.reporting.adapters.out.persistence;

import com.lms.reporting.application.port.out.CollectionCaseRepository;
import com.lms.reporting.domain.CollectionCaseInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC store for {@code collection_case_read}. {@code upsert} keeps the opened bucket/time
 * when a later event (escalation, close) carries only a status change (COALESCE on update).
 */
@Repository
public class JdbcCollectionCaseRepository implements CollectionCaseRepository {

    private final JdbcClient jdbc;

    public JdbcCollectionCaseRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void upsert(CollectionCaseInfo c) {
        jdbc.sql(
                        """
                        insert into collection_case_read (loan_id, case_id, bucket, status,
                            opened_at, updated_at)
                        values (:loanId, :caseId, :bucket, :status, :openedAt, now())
                        on conflict (loan_id) do update set
                            case_id = excluded.case_id,
                            bucket = coalesce(excluded.bucket, collection_case_read.bucket),
                            status = excluded.status,
                            opened_at = coalesce(excluded.opened_at, collection_case_read.opened_at),
                            updated_at = now()
                        """)
                .param("loanId", c.loanId())
                .param("caseId", c.caseId())
                .param("bucket", c.bucket())
                .param("status", c.status())
                .param(
                        "openedAt",
                        c.openedAt() == null ? null : java.sql.Timestamp.from(c.openedAt()))
                .update();
    }

    @Override
    public List<CollectionCaseInfo> findAll() {
        return jdbc.sql(
                        "select loan_id, case_id, bucket, status, opened_at from collection_case_read")
                .query(JdbcCollectionCaseRepository::mapRow)
                .list();
    }

    private static CollectionCaseInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
        var openedTs = rs.getTimestamp("opened_at");
        return new CollectionCaseInfo(
                rs.getObject("loan_id", java.util.UUID.class),
                rs.getString("case_id"),
                rs.getString("bucket"),
                rs.getString("status"),
                openedTs == null ? null : openedTs.toInstant());
    }
}
