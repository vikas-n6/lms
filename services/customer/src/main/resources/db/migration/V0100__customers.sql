-- Customer aggregate. Money-free in Phase 0. created_at/updated_at/version are mandated on every
-- table (CLAUDE.md "Persistence"); version supports optimistic locking in later phases.
create table if not exists customers (
    id            uuid primary key,
    full_name     text        not null,
    phone         text        not null,
    date_of_birth date        not null,
    status        text        not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    version       bigint      not null default 0
);
