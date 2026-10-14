package com.lms.loanaccount.adapters.out.persistence;

import com.lms.loanaccount.application.port.out.AccrualRepository;
import com.lms.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for daily accruals. Idempotency per {@code (loan_id, business_date,
 * kind)} is enforced by the table's primary key: a duplicate insert is caught and reported as
 * "already accrued" (docs/domain-rules.md "Interest and penal accrual").
 */
@Repository
public class JdbcAccrualRepository implements AccrualRepository {

    private static final String INTEREST = "INTEREST";

    private final JdbcClient jdbc;

    public JdbcAccrualRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean recordInterestAccrual(
            UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
        try {
            jdbc.sql(
                            """
                            insert into accruals (loan_id, business_date, kind, instalment_number,
                                currency, amount_minor)
                            values (:loanId, :date, :kind, :n, :currency, :amount)
                            """)
                    .param("loanId", loanId)
                    .param("date", businessDate)
                    .param("kind", INTEREST)
                    .param("n", instalmentNumber)
                    .param("currency", amount.currency().getCurrencyCode())
                    .param("amount", amount.minorUnits())
                    .update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false; // already accrued for this (loanId, businessDate): idempotent
        }
    }

    @Override
    public Money totalInterestAccrued(UUID loanId) {
        String currency =
                jdbc.sql(
                                "select currency from accruals where loan_id = :loanId and kind = :kind limit 1")
                        .param("loanId", loanId)
                        .param("kind", INTEREST)
                        .query(String.class)
                        .optional()
                        .orElse("INR");
        long total =
                jdbc.sql(
                                "select coalesce(sum(amount_minor), 0) from accruals where loan_id = :loanId and kind = :kind")
                        .param("loanId", loanId)
                        .param("kind", INTEREST)
                        .query(Long.class)
                        .single();
        return Money.of(total, Currency.getInstance(currency));
    }
}
