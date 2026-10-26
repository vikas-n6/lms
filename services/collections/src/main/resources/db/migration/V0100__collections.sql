-- collections schema (phase prompt task 4). collections never reads loan tables; its state is built
-- from the events it consumes. Cases are mutable (optimistic locking); dunning_actions is append-only
-- with a unique (case_id, step_id) so replays and duplicate DPD ticks never repeat an action.

create table if not exists collection_cases (
    id                    uuid        primary key,
    loan_id               text        not null unique,
    loan_number           text        not null,
    status                text        not null,  -- OPEN|PROMISE_TO_PAY|ESCALATED|RESOLVED|WRITE_OFF_RECOMMENDED|WRITTEN_OFF
    bucket                text        not null,
    days_past_due         int         not null,
    assigned_agent        text,
    promise_date          date,
    promise_amount_minor  bigint,
    currency              text,
    plan_version          int         not null,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    version               bigint      not null default 0
);
create index if not exists idx_cases_status on collection_cases (status);
create index if not exists idx_cases_bucket on collection_cases (bucket);
create index if not exists idx_cases_agent on collection_cases (assigned_agent);

create table if not exists dunning_actions (
    id            uuid        primary key,
    case_id       uuid        not null,
    step_id       text        not null,
    dpd_threshold int         not null,
    action_type   text        not null,
    channel       text,
    template_id   text,
    fired_at      timestamptz not null,
    created_at    timestamptz not null default now(),
    unique (case_id, step_id)
);
create index if not exists idx_dunning_actions_case on dunning_actions (case_id, fired_at);

-- Append-only enforcement for the action timeline (CLAUDE.md: no UPDATE/DELETE on state-history
-- rows). Enforced by a trigger so it holds even for the table owner.
create or replace function reject_mutation() returns trigger as $$
begin
    raise exception 'table % is append-only: UPDATE/DELETE is not permitted', tg_table_name;
end;
$$ language plpgsql;

create or replace trigger trg_dunning_actions_append_only
    before update or delete on dunning_actions
    for each row execute function reject_mutation();
