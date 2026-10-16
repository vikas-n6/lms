-- Ledger schema (phase prompt task 3). Entries and postings are immutable and append-only: the
-- service DB role is granted INSERT/SELECT but never UPDATE/DELETE on accounts/journal_entries/
-- postings (postgres-init, Docker session). Balances are derived by summing postings, never stored.

-- Chart of accounts (docs/domain-rules.md). Seeded once; the code is the stable key.
create table if not exists accounts (
    code        text        primary key,
    name        text        not null,
    type        text        not null,  -- ASSET | LIABILITY | INCOME | EXPENSE
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     bigint      not null default 0
);

insert into accounts (code, name, type) values
    ('1001', 'Bank (operating)', 'ASSET'),
    ('1100', 'Loan principal receivable', 'ASSET'),
    ('1110', 'Interest receivable', 'ASSET'),
    ('1130', 'Penal receivable', 'ASSET'),
    ('2100', 'GST payable', 'LIABILITY'),
    ('2200', 'Customer suspense (unallocated receipts)', 'LIABILITY'),
    ('2300', 'Disbursement in transit', 'LIABILITY'),
    ('4001', 'Interest income', 'INCOME'),
    ('4002', 'Processing fee income', 'INCOME'),
    ('4003', 'Penal income', 'INCOME'),
    ('5001', 'Bad debt written off', 'EXPENSE')
on conflict do nothing;

-- Immutable journal entries. source_event_id is unique, making posting idempotent.
create table if not exists journal_entries (
    id                 uuid        primary key,
    source_event_id    uuid        not null,
    source_event_type  text        not null,
    currency           text        not null,
    loan_id            text,
    customer_id        text,
    reverses_entry_id  uuid,
    occurred_at        timestamptz not null,
    created_at         timestamptz not null default now()
);
create unique index if not exists uq_journal_source_event on journal_entries (source_event_id);
create index if not exists idx_journal_loan on journal_entries (loan_id, occurred_at);
create index if not exists idx_journal_reverses on journal_entries (reverses_entry_id);

create table if not exists postings (
    id                uuid        primary key,
    journal_entry_id  uuid        not null references journal_entries (id),
    account_code      text        not null references accounts (code),
    side              text        not null,  -- DEBIT | CREDIT
    amount_minor      bigint      not null check (amount_minor > 0),
    currency          text        not null,
    loan_id           text,
    customer_id       text,
    created_at        timestamptz not null default now()
);
create index if not exists idx_postings_entry on postings (journal_entry_id);
create index if not exists idx_postings_account on postings (account_code);
create index if not exists idx_postings_loan on postings (loan_id);

-- Deferred constraint trigger: a journal entry's debits must equal its credits, checked at COMMIT
-- (docs/domain-rules.md "Ledger invariants" rule 2). Deferred so the entry and all its postings can
-- be inserted in one transaction before the balance is verified.
create or replace function assert_entry_balanced() returns trigger as $$
declare
    net bigint;
    entry_id uuid := coalesce(new.journal_entry_id, old.journal_entry_id);
begin
    select coalesce(sum(case when side = 'DEBIT' then amount_minor else -amount_minor end), 0)
      into net
      from postings
     where journal_entry_id = entry_id;
    if net <> 0 then
        raise exception 'unbalanced journal entry %: debit-credit net = %', entry_id, net;
    end if;
    return null;
end;
$$ language plpgsql;

drop trigger if exists trg_entry_balanced on postings;
create constraint trigger trg_entry_balanced
    after insert on postings
    deferrable initially deferred
    for each row execute function assert_entry_balanced();

-- Reconciliation runs (trial-balance integrity checks).
create table if not exists recon_runs (
    id               uuid        primary key,
    run_at           timestamptz not null,
    diff_minor_units bigint      not null,
    created_at       timestamptz not null default now()
);

-- Append-only enforcement (docs/domain-rules.md "No UPDATE or DELETE on ledger rows"). Enforced by
-- triggers so it holds even for the table owner, stronger than withholding grants; the postgres-init
-- grant revocation is belt-and-suspenders (Docker session).
create or replace function reject_mutation() returns trigger as $$
begin
    raise exception 'table % is append-only: UPDATE/DELETE is not permitted', tg_table_name;
end;
$$ language plpgsql;

create or replace trigger trg_journal_entries_append_only
    before update or delete on journal_entries
    for each row execute function reject_mutation();
create or replace trigger trg_postings_append_only
    before update or delete on postings
    for each row execute function reject_mutation();
