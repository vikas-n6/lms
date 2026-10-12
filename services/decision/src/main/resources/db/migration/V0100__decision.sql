-- Decision service schema (docs/architecture.md: owns rule_sets, decisions, bureau_snapshots).
-- Every table carries created_at/updated_at/version where mutable (CLAUDE.md "Persistence").
-- No UPDATE/DELETE on decisions/bureau_snapshots: a manual review override is a NEW decision row.

create table if not exists rule_sets (
    version     text        primary key,
    definition  jsonb       not null,
    created_at  timestamptz not null default now()
);
-- Seeded from classpath rule-sets/{version}.json by ClasspathRuleSetRepository in dev; the JDBC +
-- Redis-cached repository (Docker session) will load from this table.

create table if not exists decisions (
    id                  uuid        primary key,
    application_id      text        not null,
    customer_id         text        not null,
    requested_minor     bigint      not null,
    currency            text        not null,
    outcome             text        not null,
    reason_codes        jsonb       not null,
    rule_results        jsonb       not null,
    inputs              jsonb       not null,
    offered_limit_minor bigint,
    rule_set_version    text        not null,
    manual_override     boolean     not null default false,
    review_note         text,
    decided_at          timestamptz not null,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint      not null default 0
);
create index if not exists idx_decisions_application on decisions (application_id, decided_at desc);

create table if not exists bureau_snapshots (
    decision_id        uuid        primary key references decisions (id),
    available          boolean     not null,
    score              int         not null,
    overdue            boolean     not null,
    active_accounts    int         not null,
    bank_income_minor  bigint,
    currency           text        not null,
    created_at         timestamptz not null
);
