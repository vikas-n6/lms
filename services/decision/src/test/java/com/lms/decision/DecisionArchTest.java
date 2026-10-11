package com.lms.decision;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.lms.testkit.arch.LmsArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Platform (CLAUDE.md) and hexagonal (docs/architecture.md) rules for the decision service. */
@AnalyzeClasses(packages = "com.lms.decision", importOptions = ImportOption.DoNotIncludeTests.class)
class DecisionArchTest {

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
                    .definedBy("com.lms.decision.domain..")
                    .layer("Application")
                    .definedBy("com.lms.decision.application..")
                    .layer("Adapters")
                    .definedBy("com.lms.decision.adapters..")
                    .whereLayer("Adapters")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("Adapters")
                    .whereLayer("Domain")
                    .mayOnlyBeAccessedByLayers("Application", "Adapters")
                    .allowEmptyShould(true);
}
