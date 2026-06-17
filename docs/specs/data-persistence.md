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

### REQ-DATA-004 — `manufacturer.abbreviation` is a non-unique display label; the UEX sync is per-company resilient

`manufacturer.abbreviation` is a short display code, **not** an identity key, and is therefore
**not UNIQUE** (dropped in `V158`). Identity lives on the UNIQUE `uex_company_id` / `scwiki_uuid`
and the UNIQUE human-canonical `name`. The reason: the scheduled UEX `/companies` sync derives the
abbreviation from each company's nickname (falling back to `name`), and UEX ships *distinct*
companies that share a nickname — observed in prod (v0.5.4), two Esperia-derived companies both
reduce to `"Esperia"`. Each resolves to its own row by `uex_company_id`, so they cannot be merged;
under the old UNIQUE constraint the second company's UPDATE hit
`manufacturer_abbreviation_key` and, because the whole sweep ran as a single transaction, marked it
rollback-only — discarding every manufacturer update for the day and aborting the rest of the UEX
sweep with an `UnexpectedRollbackException`.

Consequences that must hold:

- The UEX manufacturer sync upserts **each company in its own `REQUIRES_NEW` transaction** (via the
  `self`-proxy `…WithinTransaction` pattern — see `CLAUDE.md` → Concurrency), so one row that
  fails rolls back only itself and the remaining companies still commit. A bad row may be counted
  as *skipped*; it may never roll back the batch.
- Two companies that share an abbreviation each keep their **own** row. The abbreviation fallback in
  the match chain is scoped to **unclaimed** rows (`uex_company_id IS NULL`) so the sync adopts only
  a legacy hand-seeded row and never hijacks a row already owned by another company.
- Any lookup of a manufacturer by abbreviation must be duplicate-tolerant (deterministic
  `findFirst … ORDER BY created_at`), never a bare `Optional` derived query that would throw
  `IncorrectResultSizeDataAccessException` once two rows share the code.

**Acceptance** (`UexManufacturerServiceTest`): two companies sharing an abbreviation each get their
own row; a single failing company does not abort the rest of the batch; the unclaimed-abbreviation
fallback still adopts a legacy short-named row.

## Out of scope

**Material-amount SCU-scale storage and rounding** (the `@PrePersist`/`@PreUpdate` HALF_UP-to-three-
decimals rule on the amount entities, plus the `> 0` / `PIECE`-integer validation) lives in its own
spec — [`inv-material-quantities.md`](inv-material-quantities.md) (REQ-INV-003) — which owns the
SCU/PIECE quantity rules end-to-end. It is a persistence-boundary rule, but kept with its sibling
input rules for one place to look.

Optimistic/pessimistic locking and the `…WithinTransaction` patterns — documented inline in
`CLAUDE.md` (see the concurrency note above).
