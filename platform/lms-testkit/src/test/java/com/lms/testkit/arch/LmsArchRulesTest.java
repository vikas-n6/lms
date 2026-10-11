package com.lms.testkit.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves each shared rule actually fails on a violating class (the deliberately-bad fixtures) and
 * does not fail on clean code — the acceptance requirement for Phase 0.
 */
class LmsArchRulesTest {

    private final JavaClasses fixtures =
            new ClassFileImporter().importPackages("com.lms.testkit.fixtures");

    @Test
    void threadLocalRuleFlagsTheFixture() {
        assertThat(LmsArchRules.NO_THREAD_LOCAL.evaluate(fixtures).hasViolation()).isTrue();
    }

    @Test
    void domainSpringRuleFlagsTheFixture() {
        assertThat(LmsArchRules.DOMAIN_IS_FREE_OF_SPRING.evaluate(fixtures).hasViolation())
                .isTrue();
    }

    @Test
    void bigDecimalRuleFlagsTheFixture() {
        assertThat(LmsArchRules.BIG_DECIMAL_ONLY_IN_LMS_MONEY.evaluate(fixtures).hasViolation())
                .isTrue();
    }

    @Test
    void structuredConcurrencyRuleFlagsUsageOutsideItsModule() {
        JavaClasses concurrency = new ClassFileImporter().importPackages("com.lms.concurrency");
        // Re-target the rule's allowed home elsewhere: Fanout (a real StructuredTaskScope user)
        // must then be flagged.
        assertThat(
                        LmsArchRules.structuredConcurrencyConfinedTo("com.example.allowed..")
                                .evaluate(concurrency)
                                .hasViolation())
                .isTrue();
        // The real rule, confined to com.lms.concurrency, must NOT flag that same code.
        assertThat(
                        LmsArchRules.PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY
                                .evaluate(concurrency)
                                .hasViolation())
                .isFalse();
    }

    @Test
    void rulesDoNotFlagCleanCode() {
        // MutableClock uses no ThreadLocal, BigDecimal or preview API.
        JavaClasses clean = new ClassFileImporter().importPackages("com.lms.testkit.time");
        assertThat(LmsArchRules.NO_THREAD_LOCAL.evaluate(clean).hasViolation()).isFalse();
        assertThat(LmsArchRules.BIG_DECIMAL_ONLY_IN_LMS_MONEY.evaluate(clean).hasViolation())
                .isFalse();
        assertThat(
                        LmsArchRules.PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY
                                .evaluate(clean)
                                .hasViolation())
                .isFalse();
    }
}
