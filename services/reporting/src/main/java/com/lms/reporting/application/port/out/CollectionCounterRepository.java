package com.lms.reporting.application.port.out;

import com.lms.money.Money;
import java.util.Currency;

/** Portfolio-wide billed/collected counters per currency, for collection efficiency. */
public interface CollectionCounterRepository {

    /** Add to the billed (demand) counter. */
    void addBilled(Money amount);

    /** Add to the collected counter. */
    void addCollected(Money amount);

    Money billed(Currency currency);

    Money collected(Currency currency);
}
