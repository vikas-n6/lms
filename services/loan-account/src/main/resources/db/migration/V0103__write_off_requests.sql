-- Phase 4: two-person write-off requests (phase prompt task 2). One admin raises a request; a
-- different admin approves it (the two-person rule is enforced in the domain and this table records
-- both identities). Mutable (REQUESTED -> APPROVED/REJECTED) with optimistic locking; the loan's
-- WrittenOff transition and the loan.written-off event are handled by the application.
create table if not exists write_off_requests (
    id             uuid        primary key,
    loan_id        uuid        not null,
    loan_number    text        not null,
    reason         text        not null,
    requested_by   text        not null,
    status         text        not null,  -- REQUESTED | APPROVED | REJECTED
    decided_by     text,
    decision_note  text,
    dpd_at_request int         not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null default 0
);
create index if not exists idx_write_off_requests_loan on write_off_requests (loan_id, created_at);
create index if not exists idx_write_off_requests_status on write_off_requests (status);
