-- KYC records, bank accounts and consents (phase prompt S3). Sensitive identifiers are stored only
-- as ciphertext plus a keyed blind index for lookup — never in plaintext (CLAUDE.md). Consents are
-- append-only (never updated/deleted).

create table if not exists kyc_records (
    customer_id            text        primary key,
    status                 text        not null,
    id_number_encrypted    text        not null,
    id_number_blind_index  text        not null,
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    version                bigint      not null default 0
);
create index if not exists idx_kyc_id_blind_index on kyc_records (id_number_blind_index);

create table if not exists bank_accounts (
    id                    uuid        primary key,
    customer_id           uuid        not null,
    masked_number         text        not null,
    ifsc                  text        not null,
    account_encrypted     text        not null,
    account_blind_index   text        not null,
    penny_drop_verified   boolean     not null,
    linked_at             timestamptz not null,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    version               bigint      not null default 0
);
create index if not exists idx_bank_customer on bank_accounts (customer_id);
create index if not exists idx_bank_blind_index on bank_accounts (account_blind_index);

create table if not exists consents (
    id          uuid        primary key,
    customer_id text        not null,
    type        text        not null,
    granted_at  timestamptz not null
);
create index if not exists idx_consents_customer on consents (customer_id, granted_at);
