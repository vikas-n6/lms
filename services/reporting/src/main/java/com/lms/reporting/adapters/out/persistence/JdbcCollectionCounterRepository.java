package com.lms.reporting.adapters.out.persistence;

import com.lms.money.Money;
import com.lms.reporting.application.port.out.CollectionCounterRepository;
import java.util.Currency;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring JDBC store for the per-currency billed/collected counters. */
@Repository
public class JdbcCollectionCounterRepository implements CollectionCounterRepository {

    private final JdbcClient jdbc;

    public JdbcCollectionCounterRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void addBilled(Money amount) {
        add(amount.currency(), amount.minorUnits(), 0L);
    }

    @Override
    public void addCollected(Money amount) {
        add(amount.currency(), 0L, amount.minorUnits());
    }

    private void add(Currency currency, long billedDelta, long collectedDelta) {
        jdbc.sql(
                        """
                        insert into collection_counter (currency, billed_minor, collected_minor,
                            updated_at)
                        values (:ccy, :billed, :collected, now())
                        on conflict (currency) do update set
                            billed_minor = collection_counter.billed_minor + excluded.billed_minor,
                            collected_minor = collection_counter.collected_minor
                                + excluded.collected_minor,
                            updated_at = now()
                        """)
                .param("ccy", currency.getCurrencyCode())
                .param("billed", billedDelta)
                .param("collected", collectedDelta)
                .update();
    }

    @Override
    public Money billed(Currency currency) {
        return read(currency, "billed_minor");
    }

    @Override
    public Money collected(Currency currency) {
        return read(currency, "collected_minor");
    }

    private Money read(Currency currency, String column) {
        Long value =
                jdbc.sql("select " + column + " from collection_counter where currency = :ccy")
                        .param("ccy", currency.getCurrencyCode())
                        .query(Long.class)
                        .optional()
                        .orElse(0L);
        return Money.of(value, currency);
    }
}
