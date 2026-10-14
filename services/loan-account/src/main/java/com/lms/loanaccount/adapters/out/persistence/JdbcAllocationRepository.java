package com.lms.loanaccount.adapters.out.persistence;

import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.AllocationRepository;
import com.lms.loanaccount.domain.Allocation;
import java.time.Clock;
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
        for (Allocation a : allocations) {
            jdbc.sql(
                            """
                            insert into allocations (id, loan_id, repayment_id, instalment_number,
                                component, currency, amount_minor)
                            values (:id, :loanId, :repaymentId, :n, :component, :currency, :amount)
                            """)
                    .param("id", Uuid7.generate(clock))
                    .param("loanId", loanId)
                    .param("repaymentId", repaymentId)
                    .param("n", a.instalmentNumber())
                    .param("component", a.component().name())
                    .param("currency", a.amount().currency().getCurrencyCode())
                    .param("amount", a.amount().minorUnits())
                    .update();
        }
    }
}
