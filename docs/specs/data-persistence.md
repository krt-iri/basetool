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

## Out of scope

**Material-amount SCU-scale storage and rounding** (the `@PrePersist`/`@PreUpdate` HALF_UP-to-three-
decimals rule on the amount entities, plus the `> 0` / `PIECE`-integer validation) lives in its own
spec — [`inv-material-quantities.md`](inv-material-quantities.md) (REQ-INV-003) — which owns the
SCU/PIECE quantity rules end-to-end. It is a persistence-boundary rule, but kept with its sibling
input rules for one place to look.

Optimistic/pessimistic locking and the `…WithinTransaction` patterns — documented inline in
`CLAUDE.md` (see the concurrency note above).
