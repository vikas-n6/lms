package com.lms.customer;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.lms.testkit.arch.LmsArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the platform rules (CLAUDE.md) and the hexagonal layering (docs/architecture.md) on the
 * customer service. Every service copies this test.
 */
@AnalyzeClasses(packages = "com.lms.customer", importOptions = ImportOption.DoNotIncludeTests.class)
class CustomerArchTest {

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
                    .definedBy("com.lms.customer.domain..")
                    .layer("Application")
                    .definedBy("com.lms.customer.application..")
                    .layer("Adapters")
                    .definedBy("com.lms.customer.adapters..")
                    .whereLayer("Adapters")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("Adapters")
                    .whereLayer("Domain")
                    .mayOnlyBeAccessedByLayers("Application", "Adapters")
                    .allowEmptyShould(true);
}
