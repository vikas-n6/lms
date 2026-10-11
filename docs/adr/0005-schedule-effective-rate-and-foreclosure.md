# ADR 0005: Effective annual rate (IRR) and the foreclosure-quote simplification

- Status: Accepted
- Date: 2026-10-11

## Context

`docs/domain-rules.md` ("Schedule engine" rule 7) requires the effective APR shown to the customer
to be computed from the actual cash flows (net disbursed against the EMIs), not copied from the
nominal rate, and exposes `effectiveAnnualRate()`. There is no closed form for the internal rate of
return (IRR) of an arbitrary cash-flow series, so a numeric method is needed. Separately, the
foreclosure amount (domain-rules "Interest and penal accrual") is explicitly called out as a
documented simplification.

Both computations are money maths, so per CLAUDE.md / architecture.md they must live in `lms-money`
(the only module allowed to use `BigDecimal`). `lms-schedule` consumes the results and never touches
`BigDecimal` itself (ArchUnit enforces this).

## Decision

1. **Effective annual rate = IRR by bisection.** `Amortization.effectiveAnnualRate(netDisbursed,
   instalments, periodsPerYear)` finds the per-period rate `i` solving
   `netDisbursed = Σ instalment_k / (1+i)^k`, then returns the annualised **effective** rate
   `((1+i)^periodsPerYear − 1)` as a `Rate` (percentage), rounded HALF_UP to 4 decimal places.
   - The present-value function is strictly decreasing in `i`, so bisection on `[0, hi]` converges to
     the unique non-negative root. `hi` starts at 1.0 (100% per period) and doubles until the PV at
     `hi` is below the net amount; 200 bisection iterations give far more precision than the 4 shown
     decimals.
   - If total repayments do not exceed cash received (no interest, or a subsidised loan), the rate is
     reported as 0. Negative IRRs are not reported for a lending product.
   - This is a **displayed** figure. It never feeds a stored money amount, so the 4-dp rounding and
     the effective-vs-nominal choice change no balance, schedule line, or ledger posting.

2. **Foreclosure quote = principal + accrued interest (actual/365) + unpaid penal + charge.**
   `Foreclosure.quote(...)` composes `Money` values; accrued interest uses
   `Amortization.simpleInterest(base, annualRate, days, 365, HALF_UP)`. The simplifications, as
   permitted by domain-rules:
   - **actual/365** day count for the post-last-paid-due interest (not 30/360 or actual/actual).
   - the foreclosure **charge** is a configurable rate applied to the **outstanding principal** only
     (default rate 0%).
   Live balances (outstanding principal, unpaid penal, days since last paid due) are supplied by
   loan-account in Phase 2; `lms-schedule` provides only the pure composition.

## Consequences

- The effective APR is honest about the up-front fee (it is strictly higher than the
  monthly-compounded nominal rate whenever a fee/GST is deducted), which the tests assert.
- `simpleInterest` is reused by penal accrual in Phase 2 (same actual/365 basis), keeping all
  day-count money maths in one place.
- If a future product needs a different day count or a nominal (not effective) APR display, change it
  here with a new ADR; nothing downstream hard-codes either choice.
