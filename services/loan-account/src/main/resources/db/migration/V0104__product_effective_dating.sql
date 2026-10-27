-- Phase 5 task 2: product configuration becomes effective-dated and versioned in the database
-- (docs/domain-rules.md "All product parameters live in a versioned products row"). A new version
-- is a new row with the next version number and an effective_from date; existing loans keep the
-- version they were created under (loans.product_version), so this adds versioning without changing
-- any money rule or the schedule engine (no change to Money handling).

alter table products
    add column if not exists effective_from date not null default date '2026-01-01';

-- The current product as of a date is the highest-version row whose effective_from <= that date.
create index if not exists idx_products_effective
    on products (product_id, effective_from desc, version desc);
