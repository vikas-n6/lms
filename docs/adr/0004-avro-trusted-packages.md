# ADR 0004: Avro 1.12 trusted packages for SpecificRecord deserialization

- Status: Accepted
- Date: 2026-10-11

## Context

Avro 1.12 added `ClassSecurityValidator`: when (de)serializing a `SpecificRecord`, Avro resolves the
generated class by name and refuses any class outside a trusted package allow-list, throwing
`SecurityException: ... not trusted to be included in Avro schemas`. Our generated contract classes
live under `com.lms.contracts.*`, which is not trusted by default, so the Confluent serde fails.

Also pinned for convergence: Avro 1.12.2 pulls `commons-compress` 1.28.0 while Confluent 8.3.2 pulls
1.26.1; the parent manages it to 1.28.0.

## Decision

Trust only our own contract package: set the system property
`org.apache.avro.SERIALIZABLE_PACKAGES=com.lms.contracts` (not `*`). This is set:

- in the Surefire/Failsafe configuration of modules that run the Avro serde (tests), and
- at service runtime via `JAVA_TOOL_OPTIONS` / `SERIALIZABLE_PACKAGES` in container images and
  compose for every service that consumes or produces Avro events.

We keep the allow-list narrow rather than disabling the check, so the security feature still blocks
unexpected classes.

## Consequences

The property must accompany Avro deserialization everywhere. `new-service.sh` and the compose
service definitions include it so it is not forgotten.
