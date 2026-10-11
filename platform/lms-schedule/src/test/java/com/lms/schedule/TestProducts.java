package com.lms.schedule;

import com.lms.money.Money;
import com.lms.money.Rate;
import java.util.Currency;
import java.util.List;

/** The domain-rules default micro-loan product (INR), used across the schedule tests. */
final class TestProducts {

    static final Currency INR = Currency.getInstance("INR");

    private TestProducts() {}

    static Product defaultMicroLoan() {
        return new FixedTenorReducingBalanceProduct(
                "MICRO-V1",
                1,
                INR,
                Money.of(500_000L, INR), // 5,000.00 min
                Money.of(5_000_000L, INR), // 50,000.00 max
                Money.of(50_000L, INR), // 500.00 step
                List.of(3, 6, 9, 12),
                Rate.percent(24), // APR
                Rate.percent(2), // processing fee
                Rate.percent(18), // GST on fee
                Rate.percent(36), // penal APR
                3, // grace days
                Rate.zero()); // foreclosure charge
    }
}
