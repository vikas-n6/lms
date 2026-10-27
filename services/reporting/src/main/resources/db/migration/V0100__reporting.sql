-- Reporting read models (ADR 0013). Every table here is a denormalised projection built purely
-- from the domain event stream — reporting reads no other service's database (docs/architecture.md).
-- The whole schema is rebuildable by replaying every topic from offset 0. Money is bigint minor
-- units + a currency column (CLAUDE.md). The shared outbox location supplies inbox_processed (V0001)
-- used for consumer idempotency; reporting produces no events, so it has no outbox rows.

-- One row per loan: current projection from loan.events + disbursement.events.
create table if not exists loan_read (
    loan_id                      uuid        primary key,
    application_id               text,
    customer_id                  text        not null,
    product_version              int         not null default 1,
    state                        text        not null,
    dpd                          int         not null default 0,
    bucket                       text        not null default 'CURRENT',
    principal_outstanding_minor  bigint      not null default 0,
    interest_outstanding_minor   bigint      not null default 0,
    penal_outstanding_minor      bigint      not null default 0,
    disbursed_amount_minor       bigint,
    currency                     text        not null,
    disbursal_month              text,                               -- 'YYYY-MM', null until disbursed
    created_at                   timestamptz not null default now(),
    updated_at                   timestamptz not null default now(),
    version                      bigint      not null default 0
);
create index if not exists idx_loan_read_month on loan_read (disbursal_month);
create index if not exists idx_loan_read_bucket on loan_read (bucket);

-- One row per application: cumulative funnel stages from application.events + decision.events.
create table if not exists application_read (
    application_id text        primary key,
    customer_id    text,
    submitted      boolean     not null default false,
    approved       boolean     not null default false,
    offered        boolean     not null default false,
    accepted       boolean     not null default false,
    disbursed      boolean     not null default false,
    rejected       boolean     not null default false,
    expired        boolean     not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    version        bigint      not null default 0
);

-- Portfolio-wide collection demand vs collection, per currency (event-sourced, see ADR 0013).
create table if not exists collection_counter (
    currency        text        primary key,
    billed_minor    bigint      not null default 0,
    collected_minor bigint      not null default 0,
    updated_at      timestamptz not null default now()
);

-- Append-only audit trail from audit.events (ADR 0014). Never updated or deleted.
create table if not exists audit_log (
    event_id       uuid        primary key,
    actor_id       text        not null,
    actor_roles    text        not null default '',
    action         text        not null,
    target_type    text        not null,
    target_id      text        not null,
    before_summary text,
    after_summary  text,
    outcome        text        not null,
    correlation_id text,
    trace_id       text,
    recorded_at    timestamptz not null,
    stored_at      timestamptz not null default now()
);
create index if not exists idx_audit_actor on audit_log (actor_id);
create index if not exists idx_audit_action on audit_log (action);
create index if not exists idx_audit_target on audit_log (target_type, target_id);
create index if not exists idx_audit_recorded on audit_log (recorded_at);

-- Daily portfolio snapshot series (written by the snapshot job), one row per business date.
create table if not exists daily_snapshot (
    business_date               date        primary key,
    currency                    text        not null,
    total_loans                 int         not null,
    active_loans                int         not null,
    outstanding_principal_minor bigint      not null,
    disbursed_minor             bigint      not null,
    npa_ratio_bps               bigint      not null,
    dpd_current                 int         not null,
    dpd_1_30                    int         not null,
    dpd_31_60                   int         not null,
    dpd_61_90                   int         not null,
    dpd_npa                     int         not null,
    created_at                  timestamptz not null default now()
);

-- One row per collection case (collections.events), for the ops/collection views.
create table if not exists collection_case_read (
    loan_id    uuid        primary key,
    case_id    text        not null,
    bucket     text,
    status     text        not null,
    opened_at  timestamptz,
    updated_at timestamptz not null default now()
);

-- Latest reconciliation status per run (ledger.events: ledger.recon-completed), for the ledger/
-- trial-balance dashboard. Append-only; the dashboard reads the most recent of each type.
create table if not exists recon_status (
    recon_run_id      uuid        primary key,
    recon_type        text        not null,                           -- TRIAL_BALANCE | BANK_RAIL
    currency          text        not null,
    balanced          boolean     not null,
    diff_minor        bigint      not null default 0,
    matched           int         not null default 0,
    missing_in_ledger int         not null default 0,
    missing_in_bank   int         not null default 0,
    amount_mismatch   int         not null default 0,
    duplicate         int         not null default 0,
    run_at            timestamptz not null,
    stored_at         timestamptz not null default now()
);
create index if not exists idx_recon_status_type_run on recon_status (recon_type, run_at desc);
