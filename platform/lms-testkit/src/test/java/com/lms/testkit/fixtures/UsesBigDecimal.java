package com.lms.testkit.fixtures;

import java.math.BigDecimal;

/** Deliberately uses BigDecimal outside lms-money, to prove the ArchUnit rule catches it. */
public final class UsesBigDecimal {

    public BigDecimal twice(BigDecimal value) {
        return value.add(value);
    }
}
