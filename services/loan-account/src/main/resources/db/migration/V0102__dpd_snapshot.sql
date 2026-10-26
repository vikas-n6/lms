-- Phase 4: per-loan DPD snapshot (phase prompt task 1). A mutable single-row cache per loan, not a
-- store of balances (those stay derived from schedule_items). It lets the daily DPD job be
-- idempotent per (loan_id, business_date) and publish loan.dpd-updated once more when DPD returns to
-- zero. Carries updated_at only; it is overwritten each business date (not append-only).
create table if not exists loan_dpd (
    loan_id       uuid        primary key,
    business_date date        not null,
    days_past_due int         not null,
    bucket        text        not null,
    updated_at    timestamptz not null default now()
);
