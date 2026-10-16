package com.lms.disbursement.adapters.out.persistence;

import com.lms.disbursement.application.port.out.DisbursementRepository;
import com.lms.disbursement.domain.Disbursement;
import com.lms.disbursement.domain.DisbursementStatus;
import com.lms.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for disbursements (no JPA, per CLAUDE.md). {@code save} upserts and bumps
 * the optimistic-lock {@code version}; verified at runtime in the Docker session.
 */
@Repository
public class JdbcDisbursementRepository implements DisbursementRepository {

    private static final String SELECT =
            """
            select id, loan_id, loan_number, status, principal_minor, fee_minor, gst_minor,
                   net_minor, currency, rail_ref, utr, disbursement_date, attempts,
                   initiated_published, failure_reason, created_at, updated_at, version
            from disbursements
            """;

    private final JdbcClient jdbc;

    public JdbcDisbursementRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(Disbursement d) {
        jdbc.sql(
                        """
                        insert into disbursements (id, loan_id, loan_number, status, principal_minor,
                            fee_minor, gst_minor, net_minor, currency, rail_ref, utr,
                            disbursement_date, attempts, initiated_published, failure_reason,
                            created_at, updated_at, version)
                        values (:id, :loanId, :loanNumber, :status, :principal, :fee, :gst, :net,
                            :currency, :railRef, :utr, :date, :attempts, :initiatedPublished,
                            :failureReason, :createdAt, :updatedAt, :version)
                        on conflict (id) do update set
                            status = excluded.status,
                            rail_ref = excluded.rail_ref,
                            utr = excluded.utr,
                            disbursement_date = excluded.disbursement_date,
                            attempts = excluded.attempts,
                            initiated_published = excluded.initiated_published,
                            failure_reason = excluded.failure_reason,
                            updated_at = excluded.updated_at,
                            version = disbursements.version + 1
                        """)
                .param("id", d.id())
                .param("loanId", d.loanId())
                .param("loanNumber", d.loanNumber())
                .param("status", d.status().name())
                .param("principal", d.principal().minorUnits())
                .param("fee", d.fee().minorUnits())
                .param("gst", d.gst().minorUnits())
                .param("net", d.net().minorUnits())
                .param("currency", d.net().currency().getCurrencyCode())
                .param("railRef", d.railRef())
                .param("utr", d.utr())
                .param("date", d.disbursementDate())
                .param("attempts", d.attempts())
                .param("initiatedPublished", d.initiatedPublished())
                .param("failureReason", d.failureReason())
                .param("createdAt", d.createdAt())
                .param("updatedAt", d.updatedAt())
                .param("version", d.version())
                .update();
    }

    @Override
    public Optional<Disbursement> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcDisbursementRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<Disbursement> findByLoanId(UUID loanId) {
        return jdbc.sql(SELECT + " where loan_id = :loanId")
                .param("loanId", loanId)
                .query(JdbcDisbursementRepository::mapRow)
                .optional();
    }

    @Override
    public List<Disbursement> findAll() {
        return jdbc.sql(SELECT + " order by created_at desc")
                .query(JdbcDisbursementRepository::mapRow)
                .list();
    }

    private static Disbursement mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        LocalDate date =
                rs.getDate("disbursement_date") == null
                        ? null
                        : rs.getDate("disbursement_date").toLocalDate();
        return new Disbursement(
                rs.getObject("id", UUID.class),
                rs.getObject("loan_id", UUID.class),
                rs.getString("loan_number"),
                DisbursementStatus.valueOf(rs.getString("status")),
                Money.of(rs.getLong("principal_minor"), currency),
                Money.of(rs.getLong("fee_minor"), currency),
                Money.of(rs.getLong("gst_minor"), currency),
                Money.of(rs.getLong("net_minor"), currency),
                rs.getString("rail_ref"),
                rs.getString("utr"),
                date,
                rs.getInt("attempts"),
                rs.getBoolean("initiated_published"),
                rs.getString("failure_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }
}
