> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-21.
> **Owner area:** ORDERS · **Related ADRs:** none

# Order-overview Materialien column

## Context & goal

The order-overview list (Auftragsverwaltung, `GET /orders`) shows one row per job order. Its
**Materialien** column is meant to answer, at a glance, *what has to be gathered and how far
along the gathering is* — the same question for both order kinds.

For a `MATERIAL` order this has always been the order's material requirements with a
per-material collection progress (`currentStock / amount`). For an `ITEM` order the column
previously showed the **ordered items and their delivery count** instead — a different
question (how many finished items were handed over), which broke the parallel between the two
kinds and hid the procurement progress that the order's blueprint-derived materials actually
need. This spec makes the `ITEM` column show the **aggregated material list with material
collection progress**, exactly like a `MATERIAL` order.

The aggregated material list (one row per material+quality, summed across the ordered items'
snapshotted blueprint requirements) already exists for the item-order detail page
([`orders-item-blueprint-coverage.md`](orders-item-blueprint-coverage.md) is a neighbouring
item-order view); this spec adds the per-bucket stock that the overview progress needs.

## Requirements

### REQ-ORDERS-017 — Item-order overview shows aggregated materials with collection progress

The order-overview list's **Materialien** column MUST, for an `ITEM` order, render the order's
**aggregated material list** — the materials derived and summed from the ordered items'
blueprints, one row per material — with the same three columns a `MATERIAL` order shows:
material name, total required amount (unit-formatted by the material's `quantityType`), and a
**collection progress** percentage. It MUST NOT show the ordered items or their delivery
count. A `MATERIAL` order's column is unchanged.

The progress per row is `currentStock / totalQuantity`, clamped to 100 %, where
`currentStock` is the total of inventory **linked to that order** for the material at or above
the aggregated bucket's quality floor (`GOOD` → 650, `NONE` → no floor) — the same per-bucket
sum the `MATERIAL` requirement rows use (`InventoryItemRepository`
`sumAmountByMaterialAndJobOrderAndMinQuality`). `currentStock` is carried on
`AggregatedMaterialDto` and populated by the backend for every item order it returns (list and
detail); it is `0.0` when nothing is linked. An item order whose blueprints derived no
material renders the empty-materials placeholder.

**Acceptance**

- [ ] An item order's overview row shows its aggregated material names, required amounts, and a
  collection-progress percentage — not the ordered items or a delivered/ordered count.
- [ ] The progress equals `currentStock / totalQuantity` (100 % once stock meets the requirement).
- [ ] A material order's overview column is unchanged (per-material requirement + progress).
- [ ] An item order with no derived materials shows the empty-materials placeholder.

**Enforced by:** `JobOrderListRenderTest` (frontend render),
`JobOrderServiceTest` (UpdateItemJobOrderTests — aggregated stock enrichment) · **Code:**
`JobOrderService.enrichAggregatedWithClaims`, `JobOrderItemService.aggregateMaterials`,
`AggregatedMaterialDto.currentStock`, `templates/orders-index.html` · **Issues:** #595

### REQ-ORDERS-018 — Inventory may only be linked to an order that requires its material

Because the order's material view (the `MATERIAL` requirement rows and the `ITEM` aggregated
list, REQ-ORDERS-017) is built **solely from the order's requirements** with linked stock
matched onto those rows, an inventory item linked for a material the order does **not** require
has no row to surface under — it binds stock to the order while staying invisible everywhere in
the order.

Linking an inventory item to a job order — whether on create or via the in-place
association edit (the Lager "Auftrag" picker) — MUST be rejected with HTTP `400` when the item's
material is not one of the order's **required materials**. The required-material set is
kind-agnostic: for a `MATERIAL` order its material lines, for an `ITEM` order the materials
derived and snapshotted from the ordered items' blueprints (`JobOrderItemService.requiredMaterialIds`).
The Lager "Auftrag" picker MUST hide an order that does not require the row's material; it MAY
still list the order a row is **already** assigned to, so an existing (possibly orphaned) link
stays visible and clearable. The picker filter MUST be correct for `ITEM` orders too — these
carry no `job_order_material` rows, so the reference payload exposes
`JobOrderReferenceDto.requiredMaterialIds` (both kinds) for the filter rather than the
MATERIAL-only `materials` list.

**Acceptance**

- [ ] Creating or updating an inventory item with a job-order link whose material the order does
  not require returns `400` and persists nothing.
- [ ] The same link succeeds when the order requires the material (both order kinds).
- [ ] The Lager "Auftrag" dropdown for a row offers only orders that require that row's material,
  plus the order the row is already assigned to.

**Enforced by:** `InventoryItemServiceTest` (create/update gate),
`JobOrderItemServiceTest` (`requiredMaterialIds`) · **Code:**
`InventoryItemService.createInventoryItem` / `updateInventoryItem`,
`JobOrderItemService.requiredMaterialIds`, `JobOrderReferenceDto.requiredMaterialIds`,
`JobOrderService.findAllActiveReference`, `templates/fragments/inventory-stack-entries.html`

### REQ-ORDERS-019 — Order detail surfaces orphaned linked inventory

To make pre-existing orphaned links (inventory linked before the REQ-ORDERS-018 gate, or via a
future path) discoverable, the order **detail** page MUST surface every inventory item linked to
the order whose material is **not** among the order's requirements, as a warning section listing
material, owner, location, quality and amount. The list is the order-linked inventory minus the
required-material set; it is empty (section hidden) when every linked item matches a requirement.
The link itself is undone from the Lager (setting the entry's "Auftrag" back to none). Computing
the warning MUST NOT add an N+1 to the order **list** endpoint — it is only resolved on the
detail view.

**Acceptance**

- [ ] An order with an inventory item linked for a non-required material shows the
  orphaned-inventory warning listing that item; an order with none shows no warning.
- [ ] The warning renders for both order kinds and is not computed for the order-list rows.

**Enforced by:** `JobOrderServiceTest`
(`getOrphanedLinkedInventoryReturnsOnlyLinksWhoseMaterialIsNotRequired`) · **Code:**
`JobOrderService.getOrphanedLinkedInventory`, `JobOrderController` `GET
/api/v1/orders/{id}/inventory/orphaned`, `JobOrderPageController.viewOrderDetail`,
`templates/orders-detail.html`

## Out of scope

- The item-order **detail** page's aggregated panel — it keeps its material+quality rows and
  claim columns; this change only adds the stock the overview consumes and does not alter the
  detail rendering.
- The item-handover / delivery tracking (delivered vs. outstanding finished items) — still
  shown on the detail page's ordered-items table, just no longer surfaced in the overview.
- Claim columns on the overview — claims remain a detail-page concern.

## Open questions

None.
