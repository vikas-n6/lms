# ADR 0006: --enable-preview for services that use lms-concurrency (Fanout)

- Status: Accepted
- Date: 2026-10-12

## Context

ADR 0002 scoped `--enable-preview` to the `lms-concurrency` module only and noted that "no Phase 0
module depends on lms-concurrency at runtime yet", deliberately avoiding the runtime preview flag on
services. Phase 1 changes that: the decision service must "fan out bureau pull and bank-statement
analysis in parallel with `Fanout.callAll`" (phase prompt S5), and `Fanout` is implemented with
`StructuredTaskScope`, a Java 25 **preview** API.

`lms-concurrency` is compiled with `--enable-preview`, so its class files carry the preview flag. The
JVM refuses to load a preview-compiled class unless the running JVM also has `--enable-preview`
(`UnsupportedClassVersionError: ... compiled with preview features that are unavailable`). Therefore
any JVM that loads `Fanout` — the decision service at runtime, and its Surefire/Failsafe test JVMs —
must set `--enable-preview`.

The decision service's own code does **not** use preview APIs (it calls the `Fanout` wrapper whose
signature is plain `Duration`/`List`/`Callable`). However, `javac` must *load* the preview-marked
`Fanout.class` to resolve the call, and it refuses to load a preview class file without
`--enable-preview` (`class file ... uses preview features of Java SE 25`). So the compiler needs the
flag too — which in turn marks decision's own classes as preview, matching the runtime/test JVM that
already requires it.

## Decision

Honour CLAUDE.md's rule that `--enable-preview` lives only in "compiler args, Surefire `argLine`,
Failsafe `argLine`, and the container `JAVA_TOOL_OPTIONS`, and nowhere else", applied per consuming
service:

1. **Compiler `--enable-preview`** in the decision POM — required because `javac` loads the
   preview-marked `Fanout.class` to resolve the call. Decision's classes become preview-flagged as a
   result, which is consistent with the runtime flag below (not decorative).
2. **Surefire and Failsafe `argLine`** in the decision POM carry `@{argLine} --enable-preview`
   (`@{argLine}` preserves the JaCoCo agent). This is the only way its unit tests can load `Fanout`.
3. **Container `JAVA_TOOL_OPTIONS`** for the decision service (compose + image) will include
   `--enable-preview` alongside the Avro `SERIALIZABLE_PACKAGES` from ADR 0004.

Any future service that depends on `lms-concurrency` at runtime (e.g. other fan-out call sites)
follows the same three points. Services that do not use `Fanout` keep no preview flag.

## Consequences

- The decision module's whole test JVM runs with `--enable-preview`; this is harmless for its
  non-preview classes and is required for the `Fanout` call site.
- If a later JDK promotes Structured Concurrency to final, remove the flag here and in
  `lms-concurrency` together, and drop this ADR's workaround.
- The flag must be present in the decision container image/compose definition or the service will
  fail to load `Fanout` at runtime — captured in the compose work and the Docker-session checklist.
