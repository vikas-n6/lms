-- Phase 4: bank/rail reconciliation (phase prompt task 6, ADR 0012).

-- Money-movement entries carry the external (UTR / provider) reference and the amount that moved on
-- the operating account, so the daily reconciliation can match them against the bank statement and
-- rail settlement report. Nullable: only disbursement payouts and repayment receipts set them.
-- journal_entries stays append-only (the Phase 2 trigger still rejects UPDATE/DELETE); these columns
-- are set at INSERT time.
alter table journal_entries
    add column if not exists external_reference    text,
    add column if not exists external_amount_minor bigint;
create index if not exists idx_journal_external_ref
    on journal_entries (external_reference) where external_reference is not null;

-- Extend the existing recon_runs (trial-balance) to also describe a BANK_RAIL run with its counts.
alter table recon_runs
    add column if not exists kind              text   not null default 'TRIAL_BALANCE',
    add column if not exists recon_date        date,
    add column if not exists currency          text,
    add column if not exists matched           int    not null default 0,
    add column if not exists missing_in_ledger int    not null default 0,
    add column if not exists missing_in_bank   int    not null default 0,
    add column if not exists amount_mismatch   int    not null default 0,
    add column if not exists duplicate         int    not null default 0;
alter table recon_runs alter column diff_minor_units set default 0;

-- One row per reconciled reference. Mutable only for resolution (a note / resolved flag); the
-- classification itself is never changed. An adjusting entry is raised through the ledger's reversal
-- mechanism, not by editing these rows.
create table if not exists recon_items (
    id                    uuid        primary key,
    run_id                uuid        not null references recon_runs (id),
    reference             text        not null,
    classification        text        not null,  -- MATCHED|MISSING_IN_LEDGER|MISSING_IN_BANK|AMOUNT_MISMATCH|DUPLICATE
    external_amount_minor bigint,
    ledger_amount_minor   bigint,
    currency              text        not null,
    resolved              boolean     not null default false,
    resolution_note       text,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    version               bigint      not null default 0
);
create index if not exists idx_recon_items_run on recon_items (run_id);
create index if not exists idx_recon_items_class on recon_items (classification);
