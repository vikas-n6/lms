package com.lms.repayment.adapters.out.persistence;

import com.lms.money.Money;
import com.lms.repayment.application.port.out.RepaymentRepository;
import com.lms.repayment.domain.Repayment;
import com.lms.repayment.domain.RepaymentChannel;
import com.lms.repayment.domain.RepaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for repayments (no JPA). {@code save} upserts and bumps the version. */
@Repository
public class JdbcRepaymentRepository implements RepaymentRepository {

    private static final String SELECT =
            """
            select id, loan_id, amount_minor, currency, channel, purpose, foreclosure_quote_id,
                   provider_reference, status, created_at, updated_at, version
            from repayments
            """;

    private final JdbcClient jdbc;

    public JdbcRepaymentRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void save(Repayment r) {
        jdbc.sql(
                        """
                        insert into repayments (id, loan_id, amount_minor, currency, channel,
                            purpose, foreclosure_quote_id, provider_reference, status, created_at,
                            updated_at, version)
                        values (:id, :loanId, :amount, :currency, :channel, :purpose, :quoteId,
                            :ref, :status, :createdAt, :updatedAt, :version)
                        on conflict (id) do update set
                            status = excluded.status, updated_at = excluded.updated_at,
                            version = repayments.version + 1
                        """)
                .param("id", r.id())
                .param("loanId", r.loanId())
                .param("amount", r.amount().minorUnits())
                .param("currency", r.amount().currency().getCurrencyCode())
                .param("channel", r.channel().name())
                .param("purpose", r.purpose())
                .param("quoteId", r.foreclosureQuoteId())
                .param("ref", r.providerReference())
                .param("status", r.status().name())
                .param("createdAt", r.createdAt())
                .param("updatedAt", r.updatedAt())
                .param("version", 0)
                .update();
    }

    @Override
    public Optional<Repayment> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcRepaymentRepository::mapRow)
                .optional();
    }

    @Override
    public Optional<Repayment> findByProviderReference(String providerReference) {
        return jdbc.sql(SELECT + " where provider_reference = :ref")
                .param("ref", providerReference)
                .query(JdbcRepaymentRepository::mapRow)
                .optional();
    }

    @Override
    public List<Repayment> findByLoanId(UUID loanId) {
        return jdbc.sql(SELECT + " where loan_id = :loanId order by created_at")
                .param("loanId", loanId)
                .query(JdbcRepaymentRepository::mapRow)
                .list();
    }

    private static Repayment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new Repayment(
                rs.getObject("id", UUID.class),
                rs.getObject("loan_id", UUID.class),
                Money.of(rs.getLong("amount_minor"), currency),
                RepaymentChannel.valueOf(rs.getString("channel")),
                rs.getString("purpose"),
                rs.getString("foreclosure_quote_id"),
                rs.getString("provider_reference"),
                RepaymentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
