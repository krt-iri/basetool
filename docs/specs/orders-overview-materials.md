> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-14.
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
the aggregated bucket's quality floor (`GOOD` → 700, `NONE` → no floor) — the same per-bucket
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

## Out of scope

- The item-order **detail** page's aggregated panel — it keeps its material+quality rows and
  claim columns; this change only adds the stock the overview consumes and does not alter the
  detail rendering.
- The item-handover / delivery tracking (delivered vs. outstanding finished items) — still
  shown on the detail page's ordered-items table, just no longer surfaced in the overview.
- Claim columns on the overview — claims remain a detail-page concern.

## Open questions

None.
