-- Repayment service schema (phase prompt task 3). Money is bigint minor units + currency column
-- (CLAUDE.md). The repayment service never touches the loan database.

create table if not exists mandates (
    id                  uuid        primary key,
    loan_id             uuid        not null,
    customer_id         text        not null,
    provider_mandate_id text,
    status              text        not null,                -- PENDING | ACTIVE | CANCELLED | FAILED
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint      not null default 0
);
create index if not exists idx_mandates_loan on mandates (loan_id);

create table if not exists repayments (
    id                   uuid        primary key,
    loan_id              uuid        not null,
    amount_minor         bigint      not null,
    currency             text        not null,
    channel              text        not null,               -- MANDATE | PAYMENT_LINK | MANUAL
    purpose              text,                                -- null | FORECLOSURE
    foreclosure_quote_id text,
    provider_reference   text        not null unique,         -- idempotency guard
    status               text        not null,               -- PENDING | RECEIVED | REVERSED | FAILED
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    version              bigint      not null default 0
);
create index if not exists idx_repayments_loan on repayments (loan_id);

-- Payment/mandate attempts, for traceability of what was presented to a provider.
create table if not exists payment_attempts (
    id              uuid        primary key,
    loan_id         uuid        not null,
    idempotency_key text        not null unique,
    channel         text        not null,
    status          text        not null,
    created_at      timestamptz not null default now()
);

-- Callback de-duplication on (provider_reference, status); the DB unique key is the final guard.
create table if not exists repayment_callbacks (
    provider_reference text        not null,
    status             text        not null,
    received_at        timestamptz not null default now(),
    primary key (provider_reference, status)
);
