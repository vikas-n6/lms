package com.lms.loanaccount.adapters.out.persistence;

import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.domain.Allocation;
import com.lms.loanaccount.domain.Component;
import com.lms.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring JDBC persistence for the append-only {@code allocations} table (never updated/deleted).
 */
@Repository
public class JdbcAllocationRepository implements AllocationRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcAllocationRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void saveAll(UUID loanId, String repaymentId, List<Allocation> allocations) {
        insert(loanId, repaymentId, null, allocations);
    }

    @Override
    @Transactional
    public void saveReversal(
            UUID loanId,
            String reversalRepaymentId,
            String reversedRepaymentId,
            List<Allocation> allocations) {
        insert(loanId, reversalRepaymentId, reversedRepaymentId, allocations);
    }

    private void insert(
            UUID loanId, String repaymentId, String reversalOf, List<Allocation> allocations) {
        for (Allocation a : allocations) {
            jdbc.sql(
                            """
                            insert into allocations (id, loan_id, repayment_id,
                                reversal_of_repayment_id, instalment_number, component, currency,
                                amount_minor)
                            values (:id, :loanId, :repaymentId, :reversalOf, :n, :component,
                                :currency, :amount)
                            """)
                    .param("id", Uuid7.generate(clock))
                    .param("loanId", loanId)
                    .param("repaymentId", repaymentId)
                    .param("reversalOf", reversalOf)
                    .param("n", a.instalmentNumber())
                    .param("component", a.component().name())
                    .param("currency", a.amount().currency().getCurrencyCode())
                    .param("amount", a.amount().minorUnits())
                    .update();
        }
    }

    @Override
    public List<Allocation> findByRepaymentId(UUID loanId, String repaymentId) {
        return jdbc.sql(
                        """
                        select instalment_number, component, currency, amount_minor
                        from allocations
                        where loan_id = :loanId and repayment_id = :repaymentId
                          and reversal_of_repayment_id is null
                        order by instalment_number
                        """)
                .param("loanId", loanId)
                .param("repaymentId", repaymentId)
                .query(JdbcAllocationRepository::mapRow)
                .list();
    }

    private static Allocation mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new Allocation(
                rs.getInt("instalment_number"),
                Component.valueOf(rs.getString("component")),
                Money.of(rs.getLong("amount_minor"), currency));
    }
}
