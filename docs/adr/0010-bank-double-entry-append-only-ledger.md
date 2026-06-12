# ADR-0010 — Bank ledger: append-only double-entry with compute-on-read balances

- **Status:** Proposed — becomes Accepted with the Phase 1 sign-off of epic #556
- **Date:** 2026-06-12
- **Deciders:** Repository owner (@greluc)
- **Related:** spec REQ-BANK-003..006, REQ-BANK-013 (`docs/specs/bank.md`) · ADR-0003 ·
  ADR-0009 · epic [#556](https://github.com/krt-iri/basetool/issues/556)

## Context

The bank must record deposits, withdrawals and transfers (account ↔ account,
player ↔ player, and intra-account between a player's Star Citizen characters), keep a
complete audit trail, survive a Star Citizen wipe (admin reset to zero) **without losing
history**, and produce period statements with opening/closing balances. Candidate models:

- a mutable `balance` column updated in place (simple, but history-free and
  audit-hostile);
- a single-leg booking table (one row per movement with from/to columns — transfers are
  one row, deposits have a null side);
- classic double-entry: a transaction header plus 1..n signed postings.

Constraints from the codebase: the inventory area already shipped the
append-only-plus-group-on-read pattern (ADR-0003) after the merge-on-write model caused
optimistic-locking bugs; money amounts are `BigDecimal NUMERIC(19,4)` whole-aUEC
(ADR-0002); the project has repeatedly been bitten by `@Version` collisions in multi-step
write flows (CLAUDE.md concurrency section).

## Decision

We will model the ledger as **append-only double-entry**:

- `bank_transaction` — header: type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `WIPE_RESET`,
  `REVERSAL`), initiating user (FK `ON DELETE SET NULL`), note, optional FK to a
  reversed transaction, `created_at`. Insert-only.
- `bank_posting` — leg: FK transaction, FK account, signed `NUMERIC(19,4)` amount,
  optional FK character (mandatory on `PLAYER` accounts, forbidden otherwise — DB
  CHECK). Insert-only. `TRANSFER` legs sum to zero per transaction; a `REVERSAL`'s legs
  are the negated mirror of the reversed transaction's legs (their sum is the negation
  of the original's sum — zero exactly when the original was a `TRANSFER`).
- **No UPDATE/DELETE ever** on either table; corrections are `REVERSAL` transactions
  referencing the original. The **wipe reset is itself a transaction type** — one
  `WIPE_RESET` posting per non-zero account/character — so the post-wipe zero state is
  derived the same way as every other balance and the pre-wipe history stays intact.
- **Balances are computed on read** (`SUM(amount)` grouped by account / character),
  backed by a composite index `(account_id, created_at)`. No materialized balance
  column in v1; the no-overdraft check runs inside the booking transaction with an
  atomic guard so concurrent bookings serialize on the account.

## Consequences

- **Easier:** statements, 30-day dashboard deltas and the 3-month export are pure range
  aggregations over one table; the audit question "what did the balance look like on
  date X" is answerable by construction.
- **Easier:** no `@Version` churn on hot rows — ledger rows are never updated, so the
  whole optimistic-locking trap class documented in CLAUDE.md cannot occur on bookings.
- **Easier:** wipe reset needs no special storage path, no deletes, no schema reset.
- **Harder / accepted:** balance reads cost an aggregate instead of a column read —
  acceptable at org scale (ADR-0009) and structurally identical to the accepted
  inventory pattern (ADR-0003). If volume ever demands it, a running-balance snapshot
  table can be added without changing the write model.
- **Accepted cost:** "edit a booking" does not exist as UX; users learn the reversal
  flow (which is the point — banks do not edit history).

## Alternatives considered

- **Mutable balance column (+ history table on the side)** — rejected: the history
  table inevitably drifts from the balance (two write paths); audit log and ledger
  would disagree; exactly the bug class ADR-0003 eliminated for the inventory.
- **Single-leg booking rows with from/to columns** — rejected: transfers become
  half-nullable rows, per-account aggregation needs UNIONs over both columns, and the
  per-character partitioning of player accounts (REQ-BANK-003) does not fit a single
  row cleanly. Double-entry keeps every aggregate a single GROUP BY.
- **Event sourcing with projection rebuild** — rejected: a framework-grade pattern for
  a problem the relational ledger already solves; no replay consumer exists.
- **Hard reset on wipe (truncate ledger)** — rejected: violates the explicit
  requirement that history and audit trail survive a wipe (REQ-BANK-013).

