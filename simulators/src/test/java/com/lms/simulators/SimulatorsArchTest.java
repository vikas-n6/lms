package com.lms.simulators;

import com.lms.testkit.arch.LmsArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Platform rules (CLAUDE.md) on the simulators (not a hexagonal domain service). */
@AnalyzeClasses(
        packages = "com.lms.simulators",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SimulatorsArchTest {

    @ArchTest static final ArchRule noThreadLocal = LmsArchRules.NO_THREAD_LOCAL;

    @ArchTest static final ArchRule bigDecimalConfined = LmsArchRules.BIG_DECIMAL_ONLY_IN_LMS_MONEY;

    @ArchTest
    static final ArchRule previewConfined =
            LmsArchRules.PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY;
}
