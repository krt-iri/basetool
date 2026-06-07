> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-07.
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

### REQ-INV-002 — Group-on-read display: Material → Stack

The grouped Lager views (`/inventory/my`, `/inventory/all`) present each material as a group
whose stacks are computed **in SQL** (a `GROUP BY` over the stock identity) at read time. Three
stock-identity dimensions — `jobOrder`, `mission` and `owningOrgUnit` — are nullable, so the
grouping query **left-joins** them and groups on the join aliases: a stack whose rows carry no job
order and no mission (the common case for plain Lager stock), or a personal stack with no owning
org-unit, must still surface. (A constructor-expression projection over a nullable to-one otherwise
renders an implicit inner join that silently drops every such stack — the regression that emptied
both grouped views in v0.4.0.) Each stack shows the summed amount, the amount-weighted mean
quality, the max quality and the entry count. The grouped response carries **only** the collapsed stack rows — it does **not** inline
the underlying entries (those are loaded on demand, see REQ-INV-005). The UI renders two server
levels — material group → stack row — and a stack row expands to fetch its entries. The
per-material aggregate page (`/inventory`, `AggregatedInventoryDto`) is unchanged.

**Acceptance**

- [ ] Rows sharing the stock identity appear as one stack row with the correct summed amount,
  amount-weighted mean quality and entry count.
- [ ] Rows differing in any stock-identity dimension appear as separate stacks.
- [ ] A stack whose nullable stock-identity dimensions are absent — a non-personal stack with no
  job order and no mission, or a personal stack with no owning org-unit — still appears in the
  grouped view; a `null` in a nullable dimension never hides the stack.
- [ ] The grouped response contains no per-entry rows (entries are lazy — REQ-INV-005).
- [ ] Both grouped pages render the collapsed stack rows without error.

**Enforced by:** `InventoryItemServiceAggregateTest`, `InventoryItemStackQueryTest`,
`InventoryItemStackQueryDataTest`, `InventoryPageControllerMvcTest` · **Code:**
`InventoryItemService#buildGroupedFromStacks`,
`InventoryItemRepository#findUserStacks` / `#findGlobalStacks`, `InventoryStackAggregate`,
`InventoryStackDto`, `GroupedInventoryDto`, `inventory-my.html`, `inventory-admin.html` ·
**Issues:** #466

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

### REQ-INV-005 — Entries are lazy-loaded, paginated and index-backed

A stack does not inline its entries: an append-only stack grows unboundedly as contributions
accumulate, so materialising every entry on each grouped read does not scale. Entries are
fetched on expand from `GET /api/v1/inventory/{my-inventory|all}/stack/entries`, addressed by
the stock-identity fields the stack already exposes (a `null` job-order / mission / owning
org-unit selects the rows where that association is itself absent), returned **oldest-first** by
`createdAt` and **paginated** (default 20, max 100 per page). A composite index
`idx_inventory_item_stack_key` on the inventory natural key backs both the grouped `GROUP BY`
and the per-stack entries lookup. The `/all` drill-down re-applies the same org-unit scope
predicate as the grouped view; the `/my` drill-down is owner-scoped from the JWT (no
impersonation). Per-entry actions (REQ-INV-003) operate on the fetched rows unchanged. Each
drill-down row shows the entry's amount, its job-order / mission association and the per-entry
actions (book-out, note), with the note preview rendered beside the action buttons rather than
below them; `createdAt` is the entries' ordering key, not a displayed column.

**Acceptance**

- [ ] A stack's entries are returned oldest-first by `createdAt`.
- [ ] A requested page size above 100 is clamped to 100; an absent page/size yields the first 20.
- [ ] The drill-down never returns rows outside the caller's org-unit / owner scope.
- [ ] Expanding a stack on either grouped page fetches and renders its entries without error.

**Enforced by:** `InventoryItemStackQueryTest`, `InventoryItemControllerTest`,
`InventoryPageControllerMvcTest`, `DatabaseIndexMigrationTest` · **Code:**
`InventoryItemController#getMyStackEntries` / `#getAllStackEntries`,
`InventoryItemRepository#findUserStackEntries` / `#findGlobalStackEntries`,
`InventoryPageController#viewMyStackEntries` / `#viewAllStackEntries`,
`fragments/inventory-stack-entries.html`, `V143__add_inventory_item_stack_key_index.sql` ·
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

