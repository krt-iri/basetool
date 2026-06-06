# ADR-0003 — Inventory: append-only entries with group-on-read display

- **Status:** Accepted
- **Date:** 2026-06-06
- **Deciders:** Repository owner (@greluc)
- **Related:** spec [`inventory-lager.md`](../specs/inventory-lager.md) `REQ-INV-001..004` · [`org-unit-tenancy.md`](../specs/org-unit-tenancy.md) `REQ-ORG-003` · issue #466

## Context

The Lager merged stock at write time: any new or edited `InventoryItem` whose eight-dimension
natural key (owner · material · location · quality · mission · jobOrder · personal · owning
org-unit) matched an existing row had its amount summed into that row, and the separate
contribution was discarded. Four write paths did this (create, update, book-out TRANSFER,
refinery store), plus the org-unit reconciler deduped after a mass re-stamp.

The merge was **irreversible and lossy**. Operators could not see the individual
contributions a stack was made of — who dropped off what, when, with which note — which a
squadron logistician repeatedly needs for accounting and trust. The merge also forced a
read-add-write step on a shared row, guarded by a pessimistic `SELECT … FOR UPDATE` to avoid
lost updates under concurrency.

## Decision

We will make inventory **append-only** and form stacks only at read time (group-on-read).
Every write path inserts (or edits in place) its own row and never folds into a different
row. The grouped Lager views compute display *stacks* by the natural key, summing amount and
aggregating quality, and expose the underlying entries oldest-first; the UI renders Material
→ Stack → Entries with click-to-expand. Mutating actions stay **per entry** (by id +
version); the stack row is display-and-expand only — there is no aggregate book-out with
automatic allocation in this version.

## Consequences

- Provenance is preserved: each contribution keeps its own amount, note and creation instant.
- The lost-update race disappears: no shared row is mutated, so the pessimistic merge lock and
  the `findMatchingInventoryItem(ForUpdate)` queries are deleted (a net concurrency
  simplification — the `*WithinTransaction` / bulk-update-after-loop rules are unaffected).
- More physical rows and read-time grouping; pagination on the grouped views is by stack, not
  by raw row. Job-order completion and material-collection are sum-based, so totals are
  unchanged — only row counts grow.
- A new response DTO (`InventoryStackDto`), a `createdAt` field on `InventoryItemDto`, and a
  three-level template were introduced; the frontend mirrors and `openapi.json` move in lock
  step (the `DtoOpenApiContractTest` enforces it).
- No data migration: previously-merged rows remain valid; the change is forward-only.

## Alternatives considered

- **Keep merge-on-write, add a separate provenance/audit table.** Rejected: duplicates stock
  data and leaves the primary rows lossy and authoritative-but-incomplete.
- **Append-only with no grouping (show every raw row).** Rejected: the Lager becomes unreadable
  for high-volume materials; the dynamic grouping is what keeps the overview usable.
- **Aggregate book-out with FIFO allocation across a stack.** Deferred: it reintroduces
  multi-row allocation logic and obscures which contribution was consumed; per-entry actions
  match the existing id+version architecture and ship the provenance benefit with less risk.

