package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.port.out.LoanRepository;
import com.lms.loanaccount.domain.Loan;
import com.lms.loanaccount.domain.LoanState;
import com.lms.money.Money;
import com.lms.money.Rate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for loans (no JPA, per CLAUDE.md). Optimistic locking on {@code version}.
 */
@Repository
public class JdbcLoanRepository implements LoanRepository {

    private final JdbcClient jdbc;

    public JdbcLoanRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<Loan> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcLoanRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<Loan> findByApplicationId(String applicationId) {
        return jdbc.sql(SELECT + " where application_id = :applicationId")
                .param("applicationId", applicationId)
                .query(JdbcLoanRepository::mapRow)
                .optional();
    }

    @Override
    public long nextLoanSequence() {
        return jdbc.sql("select nextval('loan_number_seq')").query(Long.class).single();
    }

    @Override
    public void insert(Loan loan) {
        jdbc.sql(
                        """
                        insert into loans (id, loan_number, application_id, customer_id, product_id,
                            product_version, currency, principal_minor, tenor_months, emi_minor,
                            fee_minor, gst_minor, net_disbursal_minor, annual_interest_rate_percent,
                            state, disbursement_date, created_at, updated_at, version)
                        values (:id, :loanNumber, :applicationId, :customerId, :productId,
                            :productVersion, :currency, :principal, :tenor, :emi, :fee, :gst, :net,
                            :rate, :state, :disbursementDate, :createdAt, :updatedAt, :version)
                        """)
                .param("id", loan.id())
                .param("loanNumber", loan.loanNumber())
                .param("applicationId", loan.applicationId())
                .param("customerId", loan.customerId())
                .param("productId", loan.productId())
                .param("productVersion", loan.productVersion())
                .param("currency", loan.principal().currency().getCurrencyCode())
                .param("principal", loan.principal().minorUnits())
                .param("tenor", loan.tenorMonths())
                .param("emi", loan.emi().minorUnits())
                .param("fee", loan.fee().minorUnits())
                .param("gst", loan.gst().minorUnits())
                .param("net", loan.netDisbursal().minorUnits())
                .param("rate", loan.annualInterestRate().asPlainString())
                .param("state", loan.state().code())
                .param("disbursementDate", loan.disbursementDate())
                .param("createdAt", java.sql.Timestamp.from(loan.createdAt()))
                .param("updatedAt", java.sql.Timestamp.from(loan.updatedAt()))
                .param("version", loan.version())
                .update();
    }

    @Override
    public void update(Loan loan) {
        int rows =
                jdbc.sql(
                                """
                                update loans set state = :state, disbursement_date = :disbursementDate,
                                    updated_at = :updatedAt, version = version + 1
                                where id = :id and version = :version
                                """)
                        .param("state", loan.state().code())
                        .param("disbursementDate", loan.disbursementDate())
                        .param("updatedAt", java.sql.Timestamp.from(loan.updatedAt()))
                        .param("id", loan.id())
                        .param("version", loan.version())
                        .update();
        if (rows == 0) {
            throw new OptimisticLockingException(
                    "loan " + loan.id() + " was modified concurrently");
        }
    }

    @Override
    public List<Loan> findAll(Optional<String> stateCode, Optional<String> customerId) {
        return jdbc.sql(
                        SELECT
                                + " where (:state is null or state = :state)"
                                + " and (:customerId is null or customer_id = :customerId)"
                                + " order by created_at")
                .param("state", stateCode.orElse(null))
                .param("customerId", customerId.orElse(null))
                .query(JdbcLoanRepository::mapRow)
                .list();
    }

    private static final String SELECT =
            """
            select id, loan_number, application_id, customer_id, product_id, product_version,
                   currency, principal_minor, tenor_months, emi_minor, fee_minor, gst_minor,
                   net_disbursal_minor, annual_interest_rate_percent, state, disbursement_date,
                   created_at, updated_at, version
            from loans
            """;

    private static Loan mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        java.sql.Date disbursed = rs.getDate("disbursement_date");
        return new Loan(
                rs.getObject("id", UUID.class),
                rs.getString("loan_number"),
                rs.getString("application_id"),
                rs.getString("customer_id"),
                rs.getString("product_id"),
                rs.getInt("product_version"),
                Money.of(rs.getLong("principal_minor"), currency),
                rs.getInt("tenor_months"),
                Money.of(rs.getLong("emi_minor"), currency),
                Money.of(rs.getLong("fee_minor"), currency),
                Money.of(rs.getLong("gst_minor"), currency),
                Money.of(rs.getLong("net_disbursal_minor"), currency),
                Rate.percent(rs.getString("annual_interest_rate_percent")),
                LoanState.fromCode(rs.getString("state")),
                disbursed == null ? null : disbursed.toLocalDate(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }
}
