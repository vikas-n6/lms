# ADR 0003: Route commons-logging through SLF4J (Spring Framework 7)

- Status: Accepted
- Date: 2026-10-11

## Context

CLAUDE.md bans `commons-logging:commons-logging` (enforcer `bannedDependencies`) to force all logging
through SLF4J and avoid classpath conflicts. In Spring Framework 6 this was free: `spring-core`
bundled `spring-jcl` and never pulled real commons-logging.

Spring Framework 7 (`spring-core` 7.0.9, used by Spring Boot 4.1.1) **dropped `spring-jcl` and now
depends on `commons-logging:commons-logging` at compile scope**. Boot 4's
`spring-boot-starter-logging` ships `logback-classic`, `log4j-to-slf4j` and `jul-to-slf4j` but **no
`jcl-over-slf4j`**, so by default real commons-logging sits on the classpath and Spring's internal
JCL calls bypass our SLF4J/JSON logging.

This is the "framework reality vs. a locked rule" case CLAUDE.md anticipates. The ban's intent — no
rogue logging implementation, everything via SLF4J — is still correct and achievable.

## Decision

Keep the enforcer ban unchanged (it is not weakened). In every module that depends on Spring:

1. Exclude `commons-logging:commons-logging` from each Spring dependency (compile and test).
2. Add `org.slf4j:jcl-over-slf4j` (version managed by the Spring Boot BOM), which provides the
   `org.apache.commons.logging` API but routes it to SLF4J.

This removes the banned implementation and still bridges JCL into SLF4J, which is exactly what the
rule was protecting. `jcl-over-slf4j` (groupId `org.slf4j`) is not on the ban list.

Maven has no global transitive exclusion, so the exclusion is declared per Spring dependency. Pure
modules with no Spring dependency (`lms-money`, `lms-concurrency`) stay bridge-free.

## Consequences

- Every Spring-using POM carries the exclusion + the `jcl-over-slf4j` dependency. `new-service.sh`
  and the reference `customer` service bake this in so it is copied, not re-derived.
- If a future Spring/Boot release reinstates a bundled JCL bridge, this can be simplified (revisit).
- The build stays green under `bannedDependencies` without disabling or narrowing the rule.
