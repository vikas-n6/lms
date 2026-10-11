package com.lms.testkit.fixtures.domain;

import org.springframework.util.Assert;

/**
 * Deliberately imports Spring from a {@code ..domain..} package, to prove the ArchUnit rule fires.
 */
public final class UsesSpringInDomain {

    public void requireNonNull(Object value) {
        Assert.notNull(value, "value");
    }
}
