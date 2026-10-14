package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.port.out.ScheduleRepository;
import com.lms.loanaccount.domain.LoanScheduleLine;
import com.lms.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring JDBC persistence for {@code schedule_items}. */
@Repository
public class JdbcScheduleRepository implements ScheduleRepository {

    private final JdbcClient jdbc;

    public JdbcScheduleRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<LoanScheduleLine> findByLoanId(UUID loanId) {
        return jdbc.sql(
                        """
                        select loan_id, instalment_number, due_date, currency, principal_minor,
                               interest_minor, paid_principal_minor, paid_interest_minor,
                               penal_accrued_minor, penal_paid_minor, provisional
                        from schedule_items where loan_id = :loanId order by instalment_number
                        """)
                .param("loanId", loanId)
                .query(JdbcScheduleRepository::mapRow)
                .list();
    }

    @Override
    @Transactional
    public void replaceSchedule(UUID loanId, List<LoanScheduleLine> lines) {
        jdbc.sql("delete from schedule_items where loan_id = :loanId")
                .param("loanId", loanId)
                .update();
        for (LoanScheduleLine line : lines) {
            insert(line);
        }
    }

    @Override
    @Transactional
    public void saveAll(List<LoanScheduleLine> lines) {
        for (LoanScheduleLine line : lines) {
            jdbc.sql(
                            """
                            update schedule_items set paid_principal_minor = :paidPrincipal,
                                paid_interest_minor = :paidInterest, penal_accrued_minor = :penalAccrued,
                                penal_paid_minor = :penalPaid, updated_at = now(), version = version + 1
                            where loan_id = :loanId and instalment_number = :n
                            """)
                    .param("paidPrincipal", line.paidPrincipal().minorUnits())
                    .param("paidInterest", line.paidInterest().minorUnits())
                    .param("penalAccrued", line.penalAccrued().minorUnits())
                    .param("penalPaid", line.penalPaid().minorUnits())
                    .param("loanId", line.loanId())
                    .param("n", line.instalmentNumber())
                    .update();
        }
    }

    private void insert(LoanScheduleLine line) {
        jdbc.sql(
                        """
                        insert into schedule_items (loan_id, instalment_number, due_date, currency,
                            principal_minor, interest_minor, paid_principal_minor, paid_interest_minor,
                            penal_accrued_minor, penal_paid_minor, provisional)
                        values (:loanId, :n, :dueDate, :currency, :principal, :interest,
                            :paidPrincipal, :paidInterest, :penalAccrued, :penalPaid, :provisional)
                        """)
                .param("loanId", line.loanId())
                .param("n", line.instalmentNumber())
                .param("dueDate", line.dueDate())
                .param("currency", line.principal().currency().getCurrencyCode())
                .param("principal", line.principal().minorUnits())
                .param("interest", line.interest().minorUnits())
                .param("paidPrincipal", line.paidPrincipal().minorUnits())
                .param("paidInterest", line.paidInterest().minorUnits())
                .param("penalAccrued", line.penalAccrued().minorUnits())
                .param("penalPaid", line.penalPaid().minorUnits())
                .param("provisional", line.provisional())
                .update();
    }

    private static LoanScheduleLine mapRow(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new LoanScheduleLine(
                rs.getObject("loan_id", UUID.class),
                rs.getInt("instalment_number"),
                rs.getObject("due_date", java.time.LocalDate.class),
                Money.of(rs.getLong("principal_minor"), currency),
                Money.of(rs.getLong("interest_minor"), currency),
                Money.of(rs.getLong("paid_principal_minor"), currency),
                Money.of(rs.getLong("paid_interest_minor"), currency),
                Money.of(rs.getLong("penal_accrued_minor"), currency),
                Money.of(rs.getLong("penal_paid_minor"), currency),
                rs.getBoolean("provisional"));
    }
}
