package com.lms.ledger;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.lms.testkit.arch.LmsArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the platform rules (CLAUDE.md) and hexagonal layering (docs/architecture.md) on the
 * ledger. In particular, money/BigDecimal stays in lms-money and the domain is Spring-free.
 */
@AnalyzeClasses(packages = "com.lms.ledger", importOptions = ImportOption.DoNotIncludeTests.class)
class LedgerArchTest {

    @ArchTest static final ArchRule noThreadLocal = LmsArchRules.NO_THREAD_LOCAL;

    @ArchTest static final ArchRule domainFreeOfSpring = LmsArchRules.DOMAIN_IS_FREE_OF_SPRING;

    @ArchTest static final ArchRule bigDecimalConfined = LmsArchRules.BIG_DECIMAL_ONLY_IN_LMS_MONEY;

    @ArchTest
    static final ArchRule previewConfined =
            LmsArchRules.PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY;

    @ArchTest
    static final ArchRule hexagonalLayering =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("com.lms.ledger.domain..")
                    .layer("Application")
                    .definedBy("com.lms.ledger.application..")
                    .layer("Adapters")
                    .definedBy("com.lms.ledger.adapters..")
                    .whereLayer("Adapters")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("Adapters")
                    .whereLayer("Domain")
                    .mayOnlyBeAccessedByLayers("Application", "Adapters")
                    .allowEmptyShould(true);
}
