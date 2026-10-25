-- Phase 3: reversing allocation rows for a returned repayment (docs/domain-rules.md "Repayment
-- allocation waterfall" rule 5: reversals are new records that undo the allocation, never edits).
-- The column links a reversing row back to the repayment it undoes; it is null for original rows.
-- allocations stays append-only (the Phase 2 trigger still rejects UPDATE/DELETE).
alter table allocations
    add column if not exists reversal_of_repayment_id text;

create index if not exists idx_allocations_repayment on allocations (loan_id, repayment_id);
