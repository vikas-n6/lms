-- loan-account schema (phase prompt task 2). loan-account owns all loan balances. Money columns are
-- bigint minor units with a currency column (CLAUDE.md "Persistence"). Mutable tables carry
-- created_at/updated_at/version (optimistic locking); the append-only tables (accruals, allocations,
-- loan_state_history) carry created_at only and are never updated or deleted — the service DB role
-- is granted INSERT/SELECT but not UPDATE/DELETE on them (postgres-init, Docker session).

-- Versioned product parameters (docs/domain-rules.md "The one product"). A loan pins its version.
create table if not exists products (
    product_id                text        not null,
    version                   int         not null,
    currency                  text        not null,
    min_principal_minor       bigint      not null,
    max_principal_minor       bigint      not null,
    principal_step_minor      bigint      not null,
    tenors                    text        not null,  -- comma-separated months, e.g. 3,6,9,12
    apr_percent               text        not null,
    fee_percent               text        not null,
    gst_percent               text        not null,
    penal_apr_percent         text        not null,
    grace_days                int         not null,
    foreclosure_charge_percent text       not null,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    version_row               bigint      not null default 0,
    primary key (product_id, version)
);

-- Seed the v1 product (INR defaults from docs/domain-rules.md). Values are configurable; the loan
-- pins this version for its life.
insert into products (product_id, version, currency, min_principal_minor, max_principal_minor,
                      principal_step_minor, tenors, apr_percent, fee_percent, gst_percent,
                      penal_apr_percent, grace_days, foreclosure_charge_percent)
values ('MICRO-V1', 1, 'INR', 500000, 5000000, 50000, '3,6,9,12', '24', '2', '18', '36', 3, '0')
on conflict do nothing;

-- Human-facing loan numbers (LN-YYYY-NNNNNN) come from this sequence, separate from the UUID id.
create sequence if not exists loan_number_seq start with 1;

create table if not exists loans (
    id                           uuid        primary key,
    loan_number                  text        not null unique,
    application_id               text        not null unique,
    customer_id                  text        not null,
    product_id                   text        not null,
    product_version              int         not null,
    currency                     text        not null,
    principal_minor              bigint      not null,
    tenor_months                 int         not null,
    emi_minor                    bigint      not null,
    fee_minor                    bigint      not null,
    gst_minor                    bigint      not null,
    net_disbursal_minor          bigint      not null,
    annual_interest_rate_percent text        not null,
    state                        text        not null,
    disbursement_date            date,
    created_at                   timestamptz not null,
    updated_at                   timestamptz not null,
    version                      bigint      not null default 0
);
create index if not exists idx_loans_customer on loans (customer_id);
create index if not exists idx_loans_state on loans (state);

create table if not exists schedule_items (
    loan_id              uuid        not null,
    instalment_number    int         not null,
    due_date             date        not null,
    currency             text        not null,
    principal_minor      bigint      not null,
    interest_minor       bigint      not null,
    paid_principal_minor bigint      not null default 0,
    paid_interest_minor  bigint      not null default 0,
    penal_accrued_minor  bigint      not null default 0,
    penal_paid_minor     bigint      not null default 0,
    provisional          boolean     not null,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    version              bigint      not null default 0,
    primary key (loan_id, instalment_number)
);

-- Daily accruals. Idempotent per (loan_id, business_date, kind): the unique key stops double accrual.
create table if not exists accruals (
    loan_id           uuid        not null,
    business_date     date        not null,
    kind              text        not null,  -- INTEREST | PENAL
    instalment_number int         not null,
    currency          text        not null,
    amount_minor      bigint      not null,
    created_at        timestamptz not null default now(),
    primary key (loan_id, business_date, kind)
);

-- Append-only allocation rows (docs/domain-rules.md waterfall rule 4). Never updated or deleted.
create table if not exists allocations (
    id                uuid        primary key,
    loan_id           uuid        not null,
    repayment_id      text        not null,
    instalment_number int         not null,
    component         text        not null,  -- PENAL | INTEREST | PRINCIPAL
    currency          text        not null,
    amount_minor      bigint      not null,
    created_at        timestamptz not null default now()
);
create index if not exists idx_allocations_loan on allocations (loan_id, created_at);

-- Append-only state history (phase prompt task 2): every transition and every rejected one.
create table if not exists loan_state_history (
    id         uuid        primary key,
    loan_id    uuid        not null,
    from_state text,
    to_state   text        not null,
    cause      text        not null,
    event_id   uuid,
    legal      boolean     not null,
    created_at timestamptz not null default now()
);
create index if not exists idx_state_history_loan on loan_state_history (loan_id, created_at);
