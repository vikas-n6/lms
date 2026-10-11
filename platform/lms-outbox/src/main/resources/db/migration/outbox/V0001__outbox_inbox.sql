-- Shared outbox + inbox tables. Services include this Flyway location
-- (spring.flyway.locations += classpath:db/migration/outbox) and start their own
-- migrations at V0100 to avoid version clashes.

create table if not exists outbox (
    id             uuid primary key,
    aggregate_type text        not null,
    aggregate_id   text        not null,
    event_type     text        not null,
    event_id       uuid        not null,
    topic          text        not null,
    msg_key        bytea,
    payload        bytea       not null,
    headers        jsonb       not null default '{}'::jsonb,
    attempts       int         not null default 0,
    last_error     text,
    created_at     timestamptz not null default now(),
    published_at   timestamptz
);

-- Dedup guard: one row per event id.
create unique index if not exists uq_outbox_event_id on outbox (event_id);
-- The relay scans unpublished rows oldest-first.
create index if not exists idx_outbox_unpublished on outbox (created_at) where published_at is null;

create table if not exists inbox_processed (
    event_id     uuid primary key,
    topic        text        not null,
    processed_at timestamptz not null default now()
);
