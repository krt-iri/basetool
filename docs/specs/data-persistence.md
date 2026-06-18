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

### REQ-DATA-004 — UEX duplicate companies of one brand merge onto a single manufacturer; the sync is per-company resilient

`manufacturer.abbreviation` is a short display code, **not** an identity key, and is therefore
**not UNIQUE** (dropped in `V158`). Identity lives on the UNIQUE `uex_company_id` / `scwiki_uuid`
and the UNIQUE human-canonical `name`.

UEX ships several **distinct** `/companies` records for the **same real-world brand** — different
ids, frequently different names, and the item-side vs. vehicle-side records split a brand's catalogue
across them. Observed in prod (v0.5.9): `87 "Esperia"` carried 43 items while `278 "Esperia
Incorporation"` carried the 7 ships + 15 more items; likewise `70 "Denim Manufacture Corporation"` /
`287 "DMC"` and `62 "Covalex Shipping"` / `293 "Covalex"`. `V158` stopped the original
abbreviation-`UNIQUE` crash by letting each such company keep its own row, but that **split the
brand**: the item sync resolves the manufacturer by `id_company` and the vehicle sync by
`id_company` too, yet the two surfaces reference *different* ids for the same brand, so no single row
keyed on one `uex_company_id` could serve both.

A manufacturer therefore **may own many UEX company ids** via the `manufacturer_uex_company` alias
table (one `uex_company_id` → one `manufacturer`). The item and vehicle syncs resolve the
manufacturer **through that alias table** (ADR-0023), so every id-variant of a brand reunites on one
row.

Consequences that must hold:

- The UEX manufacturer sync upserts **each company in its own `REQUIRES_NEW` transaction** (via the
  `self`-proxy `…WithinTransaction` pattern — see `CLAUDE.md` → Concurrency), so one row that fails
  rolls back only itself and the remaining companies still commit. A bad row may be counted as
  *skipped*; it may never roll back the batch.
- The feed is processed in **ascending `id` order**. The **canonical** company of a brand — the
  lowest `uex_company_id` — owns the row's display identity (`name` / `abbreviation` /
  `uex_company_id`). Every other company of the brand (matched by name or shared abbreviation)
  **merges** into that row: it registers its id in the alias table and only OR-s the
  `is_item_manufacturer` / `is_vehicle_manufacturer` flags; it **never** overwrites the canonical
  identity (this is what keeps the result ping-pong-free across runs). A row still unclaimed
  (`uex_company_id IS NULL`, a legacy hand-seeded / P4K-only row) is adopted as canonical by the
  first matching UEX company.
- Any lookup of a manufacturer by abbreviation must be duplicate-tolerant (deterministic
  `findFirst … ORDER BY created_at`), never a bare `Optional` derived query that would throw
  `IncorrectResultSizeDataAccessException` once a P4K- or hand-seeded row shares a code.
- `V162` is the one-time reconciliation: it creates the alias table, collapses the existing
  duplicate rows onto the lowest-id canonical (repointing the `ship_type` / `game_item` FKs, carrying
  the SC Wiki / P4K links over, OR-ing the flags) and seeds the alias table.

**Acceptance** (`UexManufacturerServiceTest`, `V162MigrationTest`): two companies sharing an
abbreviation merge onto one row with both ids aliased to it and the flags OR'd; the canonical
identity is not hijacked by the duplicate; a single failing company does not abort the rest of the
batch; the abbreviation fallback still adopts a legacy short-named row; the `V162` dedup repoints
child FKs and carries cross-source links onto the surviving canonical row.

### REQ-DATA-005 — the UEX item sync isolates each item in its own transaction

UEX assigns the **same in-game `uuid`** to several distinct item ids (a base item and its
skins/variants — e.g. ids `879`/`5457`/`5458` all carry the MaxLift tractor-beam uuid). `game_item`
keeps `external_uuid` `UNIQUE` (the cross-source join key), so such a row can collide with
`uk_game_item_external_uuid`. The UEX item sync therefore upserts **each item in its own
`REQUIRES_NEW` transaction** (the `self`-proxy `…WithinTransaction` pattern), so a colliding row
rolls back only itself and the rest of the catalogue still commits.

The reason this is mandatory: when the whole `syncItems()` run was a single transaction, the *first*
`external_uuid` violation poisoned the shared Hibernate session, and every subsequent item's
autoflush re-threw the dead insert — the entire run rolled back (observed in prod: 3376 cascade
failures, no `Finished` line, the item catalogue frozen for weeks).

**Acceptance** (`UexItemSyncServiceTest`): each item upsert runs through the `self` proxy; the
per-item `catch` keeps the run going past a failure so the remaining items still persist.

## Out of scope

**Material-amount SCU-scale storage and rounding** (the `@PrePersist`/`@PreUpdate` HALF_UP-to-three-
decimals rule on the amount entities, plus the `> 0` / `PIECE`-integer validation) lives in its own
spec — [`inv-material-quantities.md`](inv-material-quantities.md) (REQ-INV-003) — which owns the
SCU/PIECE quantity rules end-to-end. It is a persistence-boundary rule, but kept with its sibling
input rules for one place to look.

Optimistic/pessimistic locking and the `…WithinTransaction` patterns — documented inline in
`CLAUDE.md` (see the concurrency note above).
