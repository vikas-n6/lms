package com.lms.reporting.adapters.out.persistence;

import com.lms.money.Money;
import com.lms.reporting.application.port.out.LoanReadRepository;
import com.lms.reporting.domain.DpdBucketCategory;
import com.lms.reporting.domain.LoanSnapshotRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC read-model store for {@code loan_read}. {@code save} upserts the whole row. */
@Repository
public class JdbcLoanReadRepository implements LoanReadRepository {

    private static final String SELECT =
            """
            select loan_id, customer_id, product_version, state, dpd, bucket,
                   principal_outstanding_minor, interest_outstanding_minor, penal_outstanding_minor,
                   disbursed_amount_minor, currency, disbursal_month
            from loan_read
            """;

    private final JdbcClient jdbc;

    public JdbcLoanReadRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<LoanSnapshotRow> find(UUID loanId) {
        return jdbc.sql(SELECT + " where loan_id = :id")
                .param("id", loanId)
                .query(JdbcLoanReadRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<String> findApplicationId(UUID loanId) {
        return jdbc.sql("select application_id from loan_read where loan_id = :id")
                .param("id", loanId)
                .query(String.class)
                .optional();
    }

    @Override
    public void save(LoanSnapshotRow row, String applicationId) {
        jdbc.sql(
                        """
                        insert into loan_read (loan_id, application_id, customer_id, product_version,
                            state, dpd, bucket, principal_outstanding_minor,
                            interest_outstanding_minor, penal_outstanding_minor,
                            disbursed_amount_minor, currency, disbursal_month, created_at,
                            updated_at, version)
                        values (:loanId, :applicationId, :customerId, :productVersion, :state, :dpd,
                            :bucket, :principal, :interest, :penal, :disbursed, :currency, :month,
                            now(), now(), 0)
                        on conflict (loan_id) do update set
                            application_id = coalesce(excluded.application_id, loan_read.application_id),
                            customer_id = excluded.customer_id,
                            product_version = excluded.product_version,
                            state = excluded.state,
                            dpd = excluded.dpd,
                            bucket = excluded.bucket,
                            principal_outstanding_minor = excluded.principal_outstanding_minor,
                            interest_outstanding_minor = excluded.interest_outstanding_minor,
                            penal_outstanding_minor = excluded.penal_outstanding_minor,
                            disbursed_amount_minor = coalesce(excluded.disbursed_amount_minor,
                                loan_read.disbursed_amount_minor),
                            disbursal_month = coalesce(excluded.disbursal_month,
                                loan_read.disbursal_month),
                            updated_at = now(),
                            version = loan_read.version + 1
                        """)
                .param("loanId", row.loanId())
                .param("applicationId", applicationId)
                .param("customerId", row.customerId())
                .param("productVersion", row.productVersion())
                .param("state", row.state())
                .param("dpd", row.dpd())
                .param("bucket", row.bucket().name())
                .param("principal", row.principalOutstanding().minorUnits())
                .param("interest", row.interestOutstanding().minorUnits())
                .param("penal", row.penalOutstanding().minorUnits())
                .param(
                        "disbursed",
                        row.disbursedAmount() == null ? null : row.disbursedAmount().minorUnits())
                .param("currency", row.principalOutstanding().currency().getCurrencyCode())
                .param("month", row.disbursalMonth())
                .update();
    }

    @Override
    public List<LoanSnapshotRow> findAll() {
        return jdbc.sql(SELECT).query(JdbcLoanReadRepository::mapRow).list();
    }

    private static LoanSnapshotRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency ccy = Currency.getInstance(rs.getString("currency"));
        long disbursedMinor = rs.getLong("disbursed_amount_minor");
        Money disbursed = rs.wasNull() ? null : Money.of(disbursedMinor, ccy);
        return new LoanSnapshotRow(
                rs.getObject("loan_id", UUID.class),
                rs.getString("customer_id"),
                rs.getInt("product_version"),
                rs.getString("state"),
                rs.getInt("dpd"),
                DpdBucketCategory.parse(rs.getString("bucket")),
                Money.of(rs.getLong("principal_outstanding_minor"), ccy),
                Money.of(rs.getLong("interest_outstanding_minor"), ccy),
                Money.of(rs.getLong("penal_outstanding_minor"), ccy),
                disbursed,
                rs.getString("disbursal_month"));
    }
}
