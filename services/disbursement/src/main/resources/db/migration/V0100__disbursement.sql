-- Disbursement service schema (phase prompt S2). Every table carries created_at/updated_at/version
-- (optimistic locking). Money is bigint minor units plus a currency column (CLAUDE.md money rules).

create table if not exists disbursements (
    id                uuid        primary key,
    loan_id           uuid        not null unique,            -- one disbursement per loan
    loan_number       text        not null,
    status            text        not null,                  -- INITIATED | SUCCEEDED | FAILED | MANUAL_REVIEW
    principal_minor   bigint      not null,
    fee_minor         bigint      not null,
    gst_minor         bigint      not null,
    net_minor         bigint      not null,
    currency          text        not null,
    rail_ref          text,
    utr               text,
    disbursement_date date,
    attempts          int         not null default 0,
    initiated_published boolean   not null default false,    -- makes the initiated publish idempotent
    failure_reason    text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint      not null default 0
);
create index if not exists idx_disbursements_status on disbursements (status);

-- One row per payout attempt (phase prompt: "each attempt recorded in payout_attempts"). The
-- idempotency key is tied to disbursement+attempt so the rail dedups a retried POST.
create table if not exists payout_attempts (
    id              uuid        primary key,
    disbursement_id uuid        not null references disbursements (id),
    attempt_number  int         not null,
    idempotency_key text        not null unique,
    status          text        not null,                    -- PENDING | SUCCESS | FAILED | UNKNOWN
    failure_code    text,
    started_at      timestamptz not null default now(),
    finished_at     timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    unique (disbursement_id, attempt_number)
);

-- Callback de-duplication guard (docs/domain-rules.md idempotency; phase prompt: dedup on
-- (railRef, status) with Redis plus a unique constraint). The DB unique key is the final guard.
create table if not exists rail_callbacks (
    rail_ref    text        not null,
    status      text        not null,
    received_at timestamptz not null default now(),
    primary key (rail_ref, status)
);
