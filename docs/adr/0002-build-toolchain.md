# ADR 0002: Build toolchain on JDK 25 (wrapper, Spotless, preview flag, jvm.config)

- Status: Accepted
- Date: 2026-10-11

## Context

System Maven runs on Homebrew JDK 23, but the project requires JDK 25. Several build tools reach
into JDK internals that moved in JDK 25.

## Decisions

1. **Maven Wrapper is authoritative.** `./mvnw` (Maven 3.9.11, script-only distribution) is
   committed and always run with `JAVA_HOME` pointing at JDK 25. The enforcer rule
   `requireJavaVersion [25,)` fails any other JDK. CI uses Temurin 25.

2. **google-java-format version is not pinned.** 1.25.2 throws
   `NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()` on JDK 25, and Spotless'
   reflective glue is incompatible with a hand-picked 1.37.0 (`InvocationTargetException`). We let
   the Spotless Maven plugin (3.10.4, Oct 2026) choose the google-java-format build it was tested
   against, which supports JDK 25. We upgrade google-java-format only by upgrading Spotless.

3. **`.mvn/jvm.config` exports JDK compiler internals** (`--add-exports`/`--add-opens` for
   `jdk.compiler/com.sun.tools.javac.*`). google-java-format needs these to call javac internals as
   a library. Keeping them in `.mvn/jvm.config` applies them to every local and CI Maven run without
   polluting `MAVEN_OPTS`.

4. **`--enable-preview` is scoped, not global.** Per CLAUDE.md it belongs only in the compiler args,
   Surefire `argLine`, Failsafe `argLine` of `lms-concurrency`, and the container
   `JAVA_TOOL_OPTIONS` — nowhere else. The parent configures Surefire/Failsafe without the flag;
   `lms-concurrency` adds it locally. To avoid forcing the runtime preview flag on every service,
   no Phase 0 module depends on `lms-concurrency` at runtime yet.

## Consequences

The build is reproducible on JDK 25 from a clean checkout via `./mvnw clean verify`. Upgrading JDK
or Spotless may require revisiting the `jvm.config` export list.
