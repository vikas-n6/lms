# Domain rules

These rules are the specification for money paths. If code and this document disagree, the code is wrong or an ADR must change this document first.

**Currency is configurable.** The default is `INR` from `lms.currency.default`. Every money threshold in this document (loan limits, income minimums, limit bands) is an example value that lives in product or rule-set configuration, each with an explicit currency code. Wherever this document says "paise" or shows ₹, read "minor units of the configured currency". Code uses `minorUnits`, and the scale comes from ISO 4217 fraction digits (2 for INR, 0 for JPY, 3 for KWD). The Admin UI formats amounts from the currency code in API responses with `Intl.NumberFormat`, never a hard-coded symbol.

## The one product (all values are configurable defaults)

| Parameter | Default |
| --- | --- |
| Principal | 5,000 to 50,000 in steps of 500 (INR defaults, in product configuration) |
| Tenor | 3, 6, 9 or 12 monthly instalments |
| Interest | 24% APR, reducing balance, monthly rate = APR / 12 |
| Processing fee | 2% of principal, deducted at disbursement |
| GST | 18% on the processing fee only |
| Penal interest | 36% APR simple (no compounding) on overdue principal plus interest, accruing daily after the grace period |
| Grace days | 3 days after the due date before penal accrual and delinquency start |
| Due date | Same day of month as disbursement, clamped to month end |
| Foreclosure charge | 0% (field exists, configurable) |
| Partial prepayment | Not supported in v1. Excess payments are held as advance and applied to the next due instalment |

All product parameters live in a versioned `products` row. A loan stores the product version it was created under and never changes with later versions.

## Money type (`lms-money`)

- `record Money(long minorUnits, Currency currency)` with `add`, `subtract`, `negate`, `compareTo`, `isZero`, all using `Math.addExact` style overflow checks. Operations across currencies throw.
- `record Rate(...)` represents percentages. `Money.percentOf(Rate, RoundingMode)` is the only multiplication path. Default rounding is `HALF_UP` to whole minor units.
- `Money.allocate(long... weights)` splits an amount using the largest-remainder method so parts always sum exactly to the whole.
- `BigDecimal` appears only inside this module, with `MathContext.DECIMAL128` for the EMI power computation.
- Property tests (jqwik): addition is associative, `allocate` always sums to the input, and round-trips through the database and JSON are lossless.

## Schedule engine

With monthly rate `r = APR / 12 / 100`, principal `P` and instalment count `n`:

```latex
EMI = \frac{P \, r \, (1+r)^n}{(1+r)^n - 1}
```

Rules:

1. EMI is rounded `HALF_UP` to whole minor units.
2. For instalment `k`: interest = round(outstanding principal × r). Principal portion = EMI − interest.
3. The last instalment clears the residue: principal portion = remaining principal, amount = principal + interest. It may differ from the EMI by a few minor units.
4. Due dates are `disbursementDate` plus `k` months, clamped to the last day of a shorter month (31 Jan gives 28 or 29 Feb, then 31 Mar).
5. Property tests (jqwik) for every principal, tenor and rate in range: the principal portions sum exactly to `P`, no balance goes negative, interest is never negative, and total interest is within 1 minor unit per instalment of the closed-form figure.
6. The schedule is generated when the disbursement succeeds, using the actual disbursement date. Before that, the loan holds an indicative schedule flagged `provisional`.
7. The effective APR shown to the customer is computed from the cash flows (net disbursed against EMIs), not copied from the nominal rate. The engine exposes `effectiveAnnualRate()`.

## Interest and penal accrual

- **Interest income recognition:** a daily job spreads each instalment's scheduled interest evenly across the days of its period. The last day of the period takes the rounding remainder. Posting: debit Interest Receivable, credit Interest Income.
- **Penal accrual:** daily on `(overdue principal + overdue interest)` after the grace period: `round(overdue × 36% / 365)`. Posting: debit Penal Receivable, credit Penal Income.
- **Foreclosure amount:** outstanding principal plus interest accrued since the last paid due date at `APR / 365` per actual day, plus unpaid penal, plus the foreclosure charge. This is a documented simplification; record it in an ADR.
- Accrual jobs are idempotent per `(loanId, businessDate)`: a unique constraint prevents double accrual.

## Loan state machine

`LoanState` is a sealed interface. Transitions are a pure function `(state, event) -> Result<LoanState, IllegalTransition>` with an exhaustive `switch` and no `default`. Illegal transitions are rejected and audited, never ignored.

| From | Trigger | To | Guard |
| --- | --- | --- | --- |
| `Created` | schedule drafted | `PendingDisbursal` | provisional schedule exists |
| `PendingDisbursal` | `disbursement.succeeded` | `Active` | amount matches loan principal minus fee and GST |
| `PendingDisbursal` | `disbursement.failed` (after retries) or offer expiry | `Cancelled` | compensation completed |
| `Active` | DPD greater than grace days | `Delinquent` | at least one overdue instalment |
| `Delinquent` | all overdue amounts cleared | `Active` | overdue total is zero |
| `Active` or `Delinquent` | outstanding total reaches zero | `Closed` | all instalments paid |
| `Active` or `Delinquent` | full foreclosure payment allocated | `Foreclosed` | amount at least the foreclosure quote |
| `Delinquent` | admin write-off command | `WrittenOff` | DPD at least 180 and an approval record |

DPD buckets: `CURRENT` (0), `DPD_1_30`, `DPD_31_60`, `DPD_61_90`, `NPA` (91 and above). DPD counts days since the oldest unpaid due date. Terminal states: `Closed`, `Foreclosed`, `WrittenOff`, `Cancelled`.

## Ledger invariants

1. Entries are immutable. A correction is a new entry with swapped postings and `reversesEntryId` set. Nothing is updated or deleted.
2. Every journal entry balances: sum of debits equals sum of credits, per currency. Enforced by a deferred constraint trigger in Postgres and also by the domain object.
3. Every entry has a `sourceEventId`. A unique constraint on it makes posting idempotent.
4. Balances are derived by summing postings (with periodic balance snapshots as an optimisation), never stored as a mutable counter.
5. The trial balance always nets to zero. A scheduled check publishes a failure metric and event if it does not.

**Chart of accounts**

| Code | Account | Type |
| --- | --- | --- |
| 1001 | Bank (operating) | Asset |
| 1100 | Loan principal receivable | Asset |
| 1110 | Interest receivable | Asset |
| 1130 | Penal receivable | Asset |
| 2100 | GST payable | Liability |
| 2200 | Customer suspense (unallocated receipts) | Liability |
| 2300 | Disbursement in transit | Liability |
| 4001 | Interest income | Income |
| 4002 | Processing fee income | Income |
| 4003 | Penal income | Income |
| 5001 | Bad debt written off | Expense |

**Posting templates** (one template per event type, versioned)

| Event | Debit | Credit |
| --- | --- | --- |
| `disbursement.initiated` | 2300 net amount | 1001 net amount |
| `disbursement.succeeded` | 1100 principal | 2300 net, 4002 fee, 2100 GST on fee |
| `disbursement.failed` | 1001 net amount | 2300 net amount |
| Daily interest accrual (`loan.interest-accrued`) | 1110 | 4001 |
| Daily penal accrual (`loan.penal-accrued`) | 1130 | 4003 |
| `repayment.received` | 1001 amount | 2200 amount |
| `loan.repayment-allocated` | 2200 allocated total | 1130 penal, 1110 interest, 1100 principal |
| Overpayment refund | 2200 | 1001 |
| Write-off (`loan.written-off`) | 5001 principal, 4001 interest, 4003 penal | 1100, 1110, 1130 |

Net disbursed amount equals principal minus fee minus GST on fee, so the `disbursement.succeeded` entry always balances.

## Repayment allocation waterfall

1. Oldest overdue instalment first. Within an instalment the order is penal, then interest, then principal.
2. After all overdue amounts are cleared, any remainder goes to the currently due instalment in the same order.
3. A remainder beyond that stays in Customer suspense as advance, and is applied automatically when the next instalment falls due.
4. The allocation is stored as rows (`repaymentId`, `scheduleItemId`, `component`, `amount`) and is deterministic: replaying the same inputs gives the same result. A property test checks that allocated plus remaining always equals the payment.
5. Reversals (a bounced mandate debit) are new records that undo the allocation and re-post the ledger. They are never edits.

## Eligibility rules (default rule set v1)

| Rule | Pass condition | Reason code on fail |
| --- | --- | --- |
| Age | 21 to 60 | `AGE_OUT_OF_RANGE` |
| KYC | Status `VERIFIED` | `KYC_NOT_VERIFIED` |
| Bureau score | 650 or higher (simulator range 300 to 900) | `BUREAU_SCORE_LOW` |
| Bureau delinquency | No active overdue account | `BUREAU_DELINQUENT` |
| Income | Declared monthly income at least 15,000 | `INCOME_TOO_LOW` |
| Affordability | (existing EMIs + new EMI) / income at most 50% | `FOIR_EXCEEDED` |
| Active loans with us | At most one | `ACTIVE_LOAN_EXISTS` |
| Velocity | At most 3 applications per phone per 24 hours | `VELOCITY_BREACH` |

Outcomes: `APPROVE`, `REJECT`, `REFER` (score 630 to 649, or a data mismatch between declared and bureau data). Approved limit by score band: 650 to 699 up to 15,000, 700 to 749 up to 30,000, 750 and above up to 50,000, always capped at 3 times monthly income. Every decision stores the inputs snapshot, the rule set version, each rule's result and the reason codes, so it can be explained and replayed.

## Idempotency and concurrency rules

- Every state-changing REST endpoint requires an `Idempotency-Key` header. The first result is stored (Redis, backed by a Postgres unique constraint) and replayed for retries with the same key and body. The same key with a different body returns 422.
- Rail and mandate callbacks are keyed by the rail reference. Duplicates are acknowledged with 200 and processed once.
- Aggregates use optimistic locking (`version`). A conflict is retried a bounded number of times, then surfaced.
- Any operation touching a loan balance runs in one transaction that also writes its outbox row.
