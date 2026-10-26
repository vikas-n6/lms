package com.lms.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditEntryTest {

    @Test
    void createsAFullEntry() {
        AuditEntry entry =
                AuditEntry.of(
                        "admin-1",
                        "admin,analyst",
                        "product.version.created",
                        "product",
                        "MICRO_V2",
                        null,
                        "version=2; apr=24.00",
                        AuditOutcome.SUCCESS);

        assertThat(entry.actorId()).isEqualTo("admin-1");
        assertThat(entry.actorRoles()).isEqualTo("admin,analyst");
        assertThat(entry.action()).isEqualTo("product.version.created");
        assertThat(entry.targetType()).isEqualTo("product");
        assertThat(entry.targetId()).isEqualTo("MICRO_V2");
        assertThat(entry.beforeSummary()).isNull();
        assertThat(entry.afterSummary()).isEqualTo("version=2; apr=24.00");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void nullRolesBecomesEmptyString() {
        AuditEntry entry =
                AuditEntry.of("a", null, "x.y", "t", "id", null, null, AuditOutcome.SUCCESS);
        assertThat(entry.actorRoles()).isEmpty();
    }

    @Test
    void unknownActorIsAllowedAsAnExplicitPlaceholder() {
        // A system/anonymous action still produces a record; we never drop the audit trail.
        AuditEntry entry =
                AuditEntry.of(
                        AuditEntry.UNKNOWN_ACTOR,
                        "",
                        "dlt.replayed",
                        "dlt-topic",
                        "loan.events.dlt",
                        null,
                        "replayed=3",
                        AuditOutcome.SUCCESS);
        assertThat(entry.actorId()).isEqualTo("unknown");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(
                        () ->
                                AuditEntry.of(
                                        " ",
                                        "",
                                        "x.y",
                                        "t",
                                        "id",
                                        null,
                                        null,
                                        AuditOutcome.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId");
        assertThatThrownBy(
                        () ->
                                AuditEntry.of(
                                        "a", "", "  ", "t", "id", null, null, AuditOutcome.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
        assertThatThrownBy(
                        () ->
                                AuditEntry.of(
                                        "a", "", "x.y", "", "id", null, null, AuditOutcome.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetType");
        assertThatThrownBy(
                        () ->
                                AuditEntry.of(
                                        "a",
                                        "",
                                        "x.y",
                                        "t",
                                        null,
                                        null,
                                        null,
                                        AuditOutcome.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetId");
    }

    @Test
    void rejectsNullOutcome() {
        assertThatThrownBy(() -> AuditEntry.of("a", "", "x.y", "t", "id", null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
