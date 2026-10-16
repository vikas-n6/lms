package com.lms.disbursement;

import com.lms.testkit.arch.LmsArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Platform rules (CLAUDE.md) on disbursement: no ThreadLocal, a Spring-free domain,
 * money/BigDecimal confined to lms-money, and preview concurrency confined to lms-concurrency.
 * Hexagonal layering is added alongside the adapters.
 */
@AnalyzeClasses(
        packages = "com.lms.disbursement",
        importOptions = ImportOption.DoNotIncludeTests.class)
class DisbursementArchTest {

    @ArchTest static final ArchRule noThreadLocal = LmsArchRules.NO_THREAD_LOCAL;

    @ArchTest static final ArchRule domainFreeOfSpring = LmsArchRules.DOMAIN_IS_FREE_OF_SPRING;

    @ArchTest static final ArchRule bigDecimalConfined = LmsArchRules.BIG_DECIMAL_ONLY_IN_LMS_MONEY;

    @ArchTest
    static final ArchRule previewConfined =
            LmsArchRules.PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY;
}
