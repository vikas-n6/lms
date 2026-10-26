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
    private static final String PENAL = "PENAL";
    private static final String DUE_SOON = "DUE_SOON";

    private final JdbcClient jdbc;

    public JdbcAccrualRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean recordInterestAccrual(
            UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
        return insert(
                loanId,
                businessDate,
                INTEREST,
                instalmentNumber,
                amount.currency().getCurrencyCode(),
                amount.minorUnits());
    }

    @Override
    public boolean recordPenalAccrual(
            UUID loanId, LocalDate businessDate, int instalmentNumber, Money amount) {
        return insert(
                loanId,
                businessDate,
                PENAL,
                instalmentNumber,
                amount.currency().getCurrencyCode(),
                amount.minorUnits());
    }

    @Override
    public boolean recordDueSoonSent(UUID loanId, LocalDate businessDate, int instalmentNumber) {
        // No money is accrued; the row only marks that the reminder was sent on this business date.
        return insert(loanId, businessDate, DUE_SOON, instalmentNumber, "XXX", 0L);
    }

    private boolean insert(
            UUID loanId,
            LocalDate businessDate,
            String kind,
            int instalmentNumber,
            String currency,
            long amountMinor) {
        try {
            jdbc.sql(
                            """
                            insert into accruals (loan_id, business_date, kind, instalment_number,
                                currency, amount_minor)
                            values (:loanId, :date, :kind, :n, :currency, :amount)
                            """)
                    .param("loanId", loanId)
                    .param("date", businessDate)
                    .param("kind", kind)
                    .param("n", instalmentNumber)
                    .param("currency", currency)
                    .param("amount", amountMinor)
                    .update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false; // already recorded for this (loanId, businessDate, kind): idempotent
        }
    }

    @Override
    public Money totalInterestAccrued(UUID loanId) {
        return total(loanId, INTEREST);
    }

    @Override
    public Money totalPenalAccrued(UUID loanId) {
        return total(loanId, PENAL);
    }

    private Money total(UUID loanId, String kind) {
        String currency =
                jdbc.sql(
                                "select currency from accruals where loan_id = :loanId and kind = :kind limit 1")
                        .param("loanId", loanId)
                        .param("kind", kind)
                        .query(String.class)
                        .optional()
                        .orElse("INR");
        long total =
                jdbc.sql(
                                "select coalesce(sum(amount_minor), 0) from accruals where loan_id = :loanId and kind = :kind")
                        .param("loanId", loanId)
                        .param("kind", kind)
                        .query(Long.class)
                        .single();
        return Money.of(total, Currency.getInstance(currency));
    }
}
