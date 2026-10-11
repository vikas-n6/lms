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

Keep the enforcer ban unchanged (it is not weakened). The fix has two parts:

1. **One central exclusion.** The parent POM manages `org.springframework:spring-core` (version
   `${spring-framework.version}`, tracking the Boot-managed release) with an `<exclusions>` entry for
   `commons-logging:commons-logging`. Because real commons-logging only ever enters the tree through
   `spring-core`, managing `spring-core` with that exclusion removes it from **every** transitive
   path — verified: enforcer `bannedDependencies` passes across `lms-config` and `lms-security` with
   no per-artifact exclusions. (Maven applies the exclusion wherever the managed `spring-core`
   coordinate is resolved.)
2. **Bridge JCL to SLF4J.** Each Spring-using module adds `org.slf4j:jcl-over-slf4j` (version from
   the Boot BOM), which supplies the `org.apache.commons.logging` API routed to SLF4J.

This removes the banned implementation and still bridges JCL into SLF4J. `jcl-over-slf4j` (groupId
`org.slf4j`) is not on the ban list. Pure modules with no Spring dependency (`lms-money`,
`lms-concurrency`) stay bridge-free.

## Consequences

- No per-dependency `commons-logging` exclusions are needed anywhere; services just add
  `jcl-over-slf4j`. `new-service.sh` and the reference `customer` service bake that in.
- If `spring-framework.version` drifts from the Boot-managed version the managed entry must be
  updated (the enforcer `dependencyConvergence` rule will flag a mismatch).
- The build stays green under `bannedDependencies` without disabling or narrowing the rule.
