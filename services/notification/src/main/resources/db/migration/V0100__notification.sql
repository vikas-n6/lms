-- notification schema (phase prompt task 5). Deduplicate on (trigger_event_id, template_id, channel)
-- so replaying an event sends no second message. The rendered body and release time are stored so a
-- message held for quiet hours can be delivered the next morning. Mutable status with optimistic
-- locking; template texts themselves live in versioned configuration, not here.
create table if not exists notification_log (
    id                uuid        primary key,
    trigger_event_id  text        not null,
    template_id       text        not null,
    channel           text        not null,  -- SMS | EMAIL | PUSH | WHATSAPP
    status            text        not null,  -- QUEUED | SENT | FAILED | SUPPRESSED
    recipient_ref     text,
    body              text        not null,
    attempts          int         not null default 0,
    failure_code      text,
    release_at        timestamptz,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    version           bigint      not null default 0,
    unique (trigger_event_id, template_id, channel)
);
create index if not exists idx_notification_status on notification_log (status);
create index if not exists idx_notification_release on notification_log (status, release_at);
