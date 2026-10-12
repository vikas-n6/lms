-- Origination schema (docs/architecture.md: owns applications, application_events, offers).
-- The offer and accepted terms are stored as JSONB on the application; application_events is an
-- append-only status audit for the UI timeline. customer_projection is a read model fed by
-- customer.events so submission is gated without calling the customer service.
-- NOTE (backlog): phone/date_of_birth are PII held here for the flow (bureau subject / age); encrypt
-- at rest in a later pass, as the customer service already does for id number + account number.

create table if not exists applications (
    id                       uuid        primary key,
    customer_id              text        not null,
    product_id               text        not null,
    requested_minor          bigint      not null,
    currency                 text        not null,
    phone                    text        not null,
    date_of_birth            date        not null,
    declared_income_minor    bigint      not null,
    employment_type          text        not null,
    consent_data_processing  boolean     not null default false,
    consent_credit_pull      boolean     not null default false,
    consent_loan_agreement   boolean     not null default false,
    status                   text        not null,
    offer                    jsonb,
    accepted_terms           jsonb,
    offer_expires_at         timestamptz,
    created_at               timestamptz not null,
    updated_at               timestamptz not null,
    version                  bigint      not null default 0
);
create index if not exists idx_applications_status on applications (status, created_at desc);
create index if not exists idx_applications_expiry on applications (status, offer_expires_at);

-- Append-only status timeline (never updated/deleted).
create table if not exists application_events (
    id             uuid        primary key,
    application_id uuid        not null references applications (id),
    status         text        not null,
    at             timestamptz not null
);
create index if not exists idx_application_events_app on application_events (application_id, at);

-- Read model of customer KYC/bank status.
create table if not exists customer_projection (
    customer_id        text        primary key,
    kyc_verified       boolean     not null default false,
    bank_account_linked boolean    not null default false,
    updated_at         timestamptz not null
);
