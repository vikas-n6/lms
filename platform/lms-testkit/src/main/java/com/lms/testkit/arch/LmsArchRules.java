package com.lms.testkit.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The architecture rules enforced across every module (docs/architecture.md "Hexagonal rules" and
 * CLAUDE.md). Each service applies these via an ArchUnit test over its own packages. The factory
 * methods take the confining package so the same rule can be re-targeted (and proven to fail) in
 * {@code lms-testkit}'s own tests.
 */
public final class LmsArchRules {

    private LmsArchRules() {}

    /** Any {@code java.util.concurrent.StructuredTaskScope} type, including its nested types. */
    public static final DescribedPredicate<JavaClass> STRUCTURED_TASK_SCOPE_TYPES =
            new DescribedPredicate<>("a java.util.concurrent.StructuredTaskScope type") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass
                            .getFullName()
                            .startsWith("java.util.concurrent.StructuredTaskScope");
                }
            };

    /** The {@code java.lang.StableValue} preview type. */
    public static final DescribedPredicate<JavaClass> STABLE_VALUE_TYPE =
            new DescribedPredicate<>("java.lang.StableValue") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getFullName().equals("java.lang.StableValue");
                }
            };

    /**
     * No class anywhere may use {@link ThreadLocal} (propagate context with ScopedValue instead).
     */
    public static final ArchRule NO_THREAD_LOCAL =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadLocal.class)
                    .because(
                            "ThreadLocal is banned; carry RequestContext as a ScopedValue (CLAUDE.md)")
                    .allowEmptyShould(true);

    /** Classes in a {@code ..domain..} package must not depend on Spring. */
    public static final ArchRule DOMAIN_IS_FREE_OF_SPRING =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..")
                    .because("domain is pure Java in the hexagonal layout (docs/architecture.md)")
                    .allowEmptyShould(true);

    /** Only {@code lms-money} may use {@link java.math.BigDecimal}. */
    public static final ArchRule BIG_DECIMAL_ONLY_IN_LMS_MONEY =
            bigDecimalConfinedTo("com.lms.money..");

    /** Only {@code lms-concurrency} may touch the preview concurrency APIs. */
    public static final ArchRule PREVIEW_CONCURRENCY_ONLY_IN_LMS_CONCURRENCY =
            structuredConcurrencyConfinedTo("com.lms.concurrency..");

    public static ArchRule bigDecimalConfinedTo(String moneyPackage) {
        return noClasses()
                .that()
                .resideOutsideOfPackage(moneyPackage)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.math.BigDecimal")
                .because("rounding and BigDecimal live only in lms-money (docs/architecture.md)")
                .allowEmptyShould(true);
    }

    public static ArchRule structuredConcurrencyConfinedTo(String concurrencyPackage) {
        return noClasses()
                .that()
                .resideOutsideOfPackage(concurrencyPackage)
                .should()
                .dependOnClassesThat(STRUCTURED_TASK_SCOPE_TYPES.or(STABLE_VALUE_TYPE))
                .because("preview concurrency APIs are confined to lms-concurrency (CLAUDE.md)")
                .allowEmptyShould(true);
    }
}
