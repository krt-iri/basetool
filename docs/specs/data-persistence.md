> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
> **Owner area:** DB/DATA · **Migration conventions:** [`db/migration/README.md`](../../backend/src/main/resources/db/migration/README.md)

# Data & persistence

## Context & goal

The schema is owned by Flyway and validated (never auto-generated) against the entities, so
prod and test run identical, reviewed DDL. Queries avoid N+1 by construction.

> **Concurrency note.** The optimistic-locking / `…WithinTransaction` / bulk-update-in-loop
> rules are **deliberately kept inline in `CLAUDE.md` → "Concurrency"** because they are
> "read this before you touch multi-step transactions" agent guidance that must live in the
> always-read file. They are data-integrity requirements; treat that section as part of this
> spec's contract.

## Requirements

### REQ-DATA-001 — Flyway owns the schema; `ddl-auto = validate`

Every schema change is a new `V<n>__<description>.sql` in
`backend/src/main/resources/db/migration`. **Hibernate `ddl-auto` is `validate`
everywhere — never `update` or `create`.** Full conventions (destructive-ops two-phase
rule, data-migration patterns, performance/locking, test caveats, pre-merge checklist) live
in [`db/migration/README.md`](../../backend/src/main/resources/db/migration/README.md) — read
it before adding a migration.

### REQ-DATA-002 — Startup seeding

`DataInitializer` seeds roles/permissions on startup.

### REQ-DATA-003 — No N+1

Prefer `JOIN FETCH`, `@EntityGraph`, or Spring Data projections over lazy-load fan-out.

### REQ-DATA-004 — Material amounts are stored at SCU scale (≤ 3 decimals)

Every persisted material amount is normalised to the SCU granularity of **0.001** — at most three
decimal places — using commercial rounding (`RoundingMode.HALF_UP`). Rounding happens at the
persistence boundary via a `@PrePersist`/`@PreUpdate` hook on each amount-bearing entity
(`InventoryItem`, `JobOrderMaterial`, `MaterialClaim`, `JobOrderHandoverItem`), so the rule holds no
matter which path produced the value: operator input, `double` arithmetic on book-out / transfer /
handover decrements, or summed refinery yields (which can land on a binary value such as
`37.160000000000004`). `PIECE` amounts are whole numbers, so the rounding is a harmless no-op for
them. Positivity (`> 0`) and the `PIECE`-integer rule are enforced earlier — at the DTO boundary via
`@ValidQuantityAmount` (inventory create/update, refinery store, job-order materials) and inline in
the book-out / handover / claim services. This is the server-side counterpart of
[REQ-UI-010 / REQ-UI-011](ui-design-system.md); SCU values with excess precision are rounded, not
rejected, mirroring the frontend.

**Acceptance**

- [ ] An amount with more than three decimals is stored rounded HALF_UP to three (`0.0015` → `0.002`, `1.2345` → `1.235`).
- [ ] No write path stores a material amount with more than three decimals.
- [ ] A `0` or negative material amount is rejected on the guarded create/update endpoints.

**Enforced by:** `MaterialAmountRoundingTest`, `ValidQuantityAmountValidatorTest`. **Code:**
`backend/.../model/{InventoryItem,JobOrderMaterial,MaterialClaim,JobOrderHandoverItem}` (`roundAmountToScuScale`),
`backend/.../validation/ValidQuantityAmountValidator`. **Issues:** PR #465.

## Out of scope

Optimistic/pessimistic locking and the `…WithinTransaction` patterns — documented inline in
`CLAUDE.md` (see the concurrency note above).
