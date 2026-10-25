package com.lms.repayment.adapters.out.persistence;

import com.lms.repayment.application.port.out.MandateRepository;
import com.lms.repayment.domain.Mandate;
import com.lms.repayment.domain.MandateStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for mandates (no JPA). {@code save} upserts and bumps the version. */
@Repository
public class JdbcMandateRepository implements MandateRepository {

    private static final String SELECT =
            """
            select id, loan_id, customer_id, provider_mandate_id, status, created_at, updated_at
            from mandates
            """;

    private final JdbcClient jdbc;

    public JdbcMandateRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void save(Mandate m) {
        jdbc.sql(
                        """
                        insert into mandates (id, loan_id, customer_id, provider_mandate_id, status,
                            created_at, updated_at, version)
                        values (:id, :loanId, :customerId, :providerMandateId, :status, :createdAt,
                            :updatedAt, 0)
                        on conflict (id) do update set
                            status = excluded.status, updated_at = excluded.updated_at,
                            version = mandates.version + 1
                        """)
                .param("id", m.id())
                .param("loanId", m.loanId())
                .param("customerId", m.customerId())
                .param("providerMandateId", m.providerMandateId())
                .param("status", m.status().name())
                .param("createdAt", m.createdAt())
                .param("updatedAt", m.updatedAt())
                .update();
    }

    @Override
    public Optional<Mandate> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcMandateRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<Mandate> findActiveByLoanId(UUID loanId) {
        return jdbc.sql(SELECT + " where loan_id = :loanId and status = 'ACTIVE' limit 1")
                .param("loanId", loanId)
                .query(JdbcMandateRepository::mapRow)
                .optional();
    }

    private static Mandate mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Mandate(
                rs.getObject("id", UUID.class),
                rs.getObject("loan_id", UUID.class),
                rs.getString("customer_id"),
                rs.getString("provider_mandate_id"),
                MandateStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
