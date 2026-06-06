> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
> **Owner area:** INV · **Related ADRs:** ADR-0003

# Inventory Lager — append-only entries & group-on-read

## Context & goal

The squadron Lager holds every contribution of refined and raw material (manual entry,
refinery store, transfer, hand-over). Historically the Lager **merged** stock at write
time: a new row whose stock identity matched an existing row had its amount summed into
that row, and the separate contribution was discarded. The merge was irreversible, so the
provenance of each drop-off (who, when, the per-entry note) was lost — a "100 SCU" row
could really be four separate contributions.

This spec replaces that with **append-only persistence + group-on-read display**: every
contribution is its own row, and the UI collapses rows that share a stock identity into one
display *stack* that expands to the individual entries. Tracked by issue #466 (milestone
v0.4.0); the persistence-model decision is recorded in ADR-0003.

The **stock identity** ("stack key") is the inventory natural key: owner (`user`),
`material`, `location`, `quality`, the optional `mission` / `jobOrder` association, the
`personal` flag, and the owning org-unit pool (`owningOrgUnit`).

## Requirements

### REQ-INV-001 — Inventory is append-only; no write path merges

No write path may fold a new or edited `InventoryItem` into a different existing row. Each
of create, update, book-out **TRANSFER** (the moved quantity at the target), refinery store
and any future inbound path inserts (or edits in place) its own row. Two rows that share the
stock identity coexist as separate rows; they are never summed in the database. The former
read-add-write merge — and the pessimistic lock that guarded its lost-update race — are
removed.

**Acceptance**

- [ ] Creating stock that matches an existing row's stock identity yields a second row; the
  existing row's amount is unchanged.
- [ ] Updating a row never deletes it in favour of a matching row; it is saved in place.
- [ ] A partial TRANSFER decrements the source and inserts a new row at the target even when
  an identical target stack already exists; the existing target row is unchanged.
- [ ] Storing a refinery output inserts a new row with its own note; no existing row's amount
  or note changes.

**Enforced by:** `InventoryItemServiceTest`, `InventoryItemServiceBookOutTest`,
`RefineryOrderServiceTest` · **Code:** `InventoryItemService`, `RefineryOrderService` ·
**Issues:** #466

### REQ-INV-002 — Group-on-read display: Material → Stack → Entries

The grouped Lager views (`/inventory/my`, `/inventory/all`) present each material as a group
whose stacks are computed at read time by the stock identity. Each stack shows the summed
amount, the amount-weighted mean quality, the max quality and the entry count; its entries
are the underlying rows, ordered **oldest-first** by creation instant. The UI renders three
levels — material group → stack row → expandable entries — so a stack row can be expanded to
see the individual entries it consists of. The per-material aggregate page (`/inventory`,
`AggregatedInventoryDto`) is unchanged.

**Acceptance**

- [ ] Rows sharing the stock identity appear as one stack row with the correct summed amount
  and entry count.
- [ ] Rows differing in any stock-identity dimension appear as separate stacks.
- [ ] A stack's entries are returned oldest-first by `createdAt`.
- [ ] Both grouped pages render without error for a stack carrying multiple entries.

**Enforced by:** `InventoryItemServiceAggregateTest`, `InventoryPageControllerMvcTest` ·
**Code:** `InventoryItemService#aggregateInventoryItems`, `InventoryStackDto`,
`GroupedInventoryDto`, `inventory-my.html`, `inventory-admin.html` · **Issues:** #466

### REQ-INV-003 — Actions operate per entry

Every mutating Lager action — book-out (consume / transfer / sell), note edit, delivered
toggle, association change, bulk check-out — targets a single `InventoryItem` by id and
`version`. The grouped stack row is display-and-expand only; it carries no aggregate
mutation. Optimistic locking and the frontend `data-version` DOM-sync therefore continue to
work unchanged at the entry level.

**Acceptance**

- [ ] Book-out / note / delivered / association endpoints accept an item id + version and
  affect only that row.
- [ ] The expanded entry view exposes those actions per entry; the stack row exposes none.

**Enforced by:** `InventoryItemControllerTest`, `InventoryPageControllerMvcTest` · **Code:**
`InventoryItemController`, `InventoryPageController` · **Issues:** #466

### REQ-INV-004 — Org-unit reconcile re-stamps without merging

When a user gains their first or loses their last org-unit membership, the reconciler
re-stamps `owning_org_unit` on their non-personal rows (promote `NULL` → the joined unit, or
demote all → `NULL`). It must **not** merge rows that become identical after the re-stamp;
they remain separate and are collapsed only for display.

**Acceptance**

- [ ] Demoting a user's rows to `NULL` leaves every row present with its amount unchanged; no
  row is deleted.

**Enforced by:** `InventoryOrgUnitReconcilerTest` · **Code:** `InventoryOrgUnitReconciler` ·
**Issues:** #466

## Out of scope

- Tenancy / visibility scope of inventory (strict-staffel Lager-View) is governed by
  [`org-unit-tenancy.md`](org-unit-tenancy.md) `REQ-ORG-003`; this spec does not change it.
  Grouping is a display concern and never widens visibility.
- The optimistic-locking, `@Version` and `*WithinTransaction` concurrency rules live in
  [`data-persistence.md`](data-persistence.md) and `CLAUDE.md`.

## Open questions

- A convenience "book out N from the whole stack" with automatic allocation across entries
  (FIFO) was deferred; per-entry actions were chosen for v1 (ADR-0003). Revisit if operators
  ask for it.

