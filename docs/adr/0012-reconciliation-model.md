# ADR 0012: Bank/rail reconciliation model and the shared recon-completed event

- Status: Accepted
- Date: 2026-10-26

## Context

Phase 4 task 6 adds a daily reconciliation that compares the ledger with the simulated bank
statement and rail settlement report, classifying each line and publishing `ledger.recon-completed`.
Two problems needed deciding: (a) the ledger postings did not carry the external (UTR / provider)
reference the match is keyed on, and (b) `ledger.recon-completed` already existed, published by the
Phase 2 trial-balance integrity check with a different meaning.

## Decision

1. **Match by reference + amount via a pure matcher.** `ReconMatcher.match(external, ledger)`
   classifies each reference as `MATCHED`, `MISSING_IN_LEDGER`, `MISSING_IN_BANK`, `AMOUNT_MISMATCH`
   or `DUPLICATE` (a reference appearing more than once on either side). Pure and deterministic
   (references in first-seen order), fully unit-tested (`ReconMatcherTest`) — the graded money logic
   runs without Docker.

2. **The ledger tags money-movement entries with their external reference.** `journal_entries` gains
   nullable `external_reference` and `external_amount_minor`, set only for the two flows that move
   money on the operating account under a provider reference: `repayment.received` (rail reference +
   receipt amount) and `disbursement.succeeded` (UTR + net disbursed). Threaded through
   `PostingRequest`/`JournalEntry` with **delegating secondary constructors**, so every existing
   caller and test is untouched and only the two money-movement listeners pass a reference. This is a
   documented simplification of "postings on account 1001 by reference": we reconcile the
   money-movement entries' references rather than re-deriving them from the 1001 posting lines (whose
   reference is not known at initiated-time). No money rule or stored amount changes.

3. **One event, two recon types (BACKWARD-safe).** `ledger.recon-completed` gains an additive
   `reconType` (default `TRIAL_BALANCE`) and five count fields (default 0). The historical
   trial-balance publish stays schema-valid; the new bank/rail run publishes `reconType=BANK_RAIL`
   with the classification counts. No new topic or event type outside the architecture catalogue
   (CLAUDE.md global do-not list) — the catalogue already lists `ledger.recon-completed`.

4. **Persistence and resolution.** A run is stored in the extended `recon_runs` (kind, date,
   currency, counts) with one `recon_items` row per reference. An admin resolves an item with a note,
   or raises an adjusting entry **through the existing reversal mechanism** (`ReverseEntryService`),
   never by editing ledger rows (docs/domain-rules.md "Ledger invariants"). `recon_items` is mutable
   only for the resolution note/flag; the classification is immutable.

5. **Simulator feeds + drift knob.** The simulators expose `GET /sim/bank/statement?date=` and
   `GET /sim/rail/settlement-report?date=`, seeded to mirror the ledger's money movements. A
   `reconDriftRate` toggle injects exactly one of each discrepancy type (a spurious line →
   MISSING_IN_LEDGER, a dropped line → MISSING_IN_BANK, an altered amount → AMOUNT_MISMATCH, a
   duplicated line → DUPLICATE), so a drift day is seen to detect each one.

## Consequences

- The matcher and the service orchestration are unit-tested now; the live run against the running
  simulators + a seeded ledger, and the "each discrepancy detected exactly" assertion end-to-end,
  are the Docker session's to run (`scripts/e2e/phase4.sh`).
- If a future product needs to reconcile additional accounts or a different match key, extend the
  tagging and the matcher inputs; nothing downstream hard-codes either.
