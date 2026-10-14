package com.lms.loanaccount.adapters.out.persistence;

import com.lms.events.Uuid7;
import com.lms.loanaccount.application.port.out.StateHistoryRepository;
import com.lms.loanaccount.domain.LoanState;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC persistence for the append-only {@code loan_state_history} table. */
@Repository
public class JdbcStateHistoryRepository implements StateHistoryRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcStateHistoryRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void append(
            UUID loanId, LoanState from, LoanState to, String cause, UUID eventId, boolean legal) {
        jdbc.sql(
                        """
                        insert into loan_state_history (id, loan_id, from_state, to_state, cause,
                            event_id, legal)
                        values (:id, :loanId, :fromState, :toState, :cause, :eventId, :legal)
                        """)
                .param("id", Uuid7.generate(clock))
                .param("loanId", loanId)
                .param("fromState", from == null ? null : from.code())
                .param("toState", to.code())
                .param("cause", cause)
                .param("eventId", eventId)
                .param("legal", legal)
                .update();
    }

    @Override
    public java.util.List<Entry> findByLoanId(UUID loanId) {
        return jdbc.sql(
                        """
                        select from_state, to_state, cause, legal, created_at
                        from loan_state_history where loan_id = :loanId order by created_at
                        """)
                .param("loanId", loanId)
                .query(
                        (rs, rowNum) ->
                                new Entry(
                                        rs.getString("from_state"),
                                        rs.getString("to_state"),
                                        rs.getString("cause"),
                                        rs.getBoolean("legal"),
                                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
