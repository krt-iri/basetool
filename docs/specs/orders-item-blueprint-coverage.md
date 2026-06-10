> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-10.
> **Owner area:** ORDERS · **Related ADRs:** none

# Item-order blueprint coverage

## Context & goal

An `ITEM` job order (Auftrag) requests finished items to be crafted, each line pinned to a
specific SC Wiki blueprint. To plan who can actually build the order, the processing
squadron/SK needs to know **which of its members already own the blueprints** for the
ordered items — and, per member, **which** of those blueprints they hold.

This spec governs the **blueprint-coverage view** rendered in the item-order detail page,
directly after the *Bearbeiter* (assignees) section. It bridges three existing concepts: the
order's required item lines (`JobOrderItem.blueprint` → output name), the per-user blueprint
ownership of the personal-inventory feature (`PersonalBlueprint`, keyed by the normalized
`product_key`, see [`blueprint-import-name-matching.md`](blueprint-import-name-matching.md)),
and org-unit membership ([`org-unit-tenancy.md`](org-unit-tenancy.md)). It deliberately
mirrors the org-unit blueprint-availability overview (#364) but scopes to one order's
required products and its responsible org unit.

## Requirements

### REQ-ORDERS-015 — Blueprint-coverage view for item orders

The item-order detail page MUST show, for an `ITEM` order, who among the members of the
order's **responsible (processing) org unit** owns the blueprint for each required item, and
which of those required blueprints each member holds. The view is **person-centric** — one
row per owning member with the required products they own — and additionally surfaces a
**per-item coverage** summary (each distinct required product with the count of members who
own its blueprint, flagging products **no member** owns as a coverage gap).

Matching is by the normalized blueprint `product_key`: a required line's
`blueprint.outputName` is normalized through `BlueprintNameNormalizer` (the same identity used
for `PersonalBlueprint.productKey`), so a member "has the blueprint for an item" exactly when
they own a `PersonalBlueprint` whose `product_key` equals the line's. Distinct required
products are de-duplicated by `product_key`. A `MATERIAL` order yields an empty view. A
member's blueprints that are **not** among the order's required products are never exposed,
and owners are identified by display name only (never the Keycloak `sub` or e-mail).

**Acceptance**

- [ ] For an item order, a member of the responsible org unit who owns a `PersonalBlueprint`
  matching a required item appears in the owners list with that product's display name.
- [ ] A required product no member owns shows owner count `0` (a gap) in the coverage summary.
- [ ] A member owning none of the required blueprints does not appear in the owners list.
- [ ] Owner blueprints outside the order's required products are not listed for that member.
- [ ] A material order returns an empty coverage view.

**Enforced by:** `JobOrderItemBlueprintOwnersServiceTest` · **Code:**
`JobOrderItemBlueprintOwnersService`, `JobOrderController.getItemBlueprintOwners` · **Issues:** —

### REQ-ORDERS-016 — Coverage view is restricted to the responsible org unit's members

The blueprint-coverage view MUST be visible **only to members of the order's responsible
squadron/SK** (and to admins, who hold system-wide oversight). This is **stricter** than the
order's own visibility: an SK-responsible order is publicly readable by every profit-eligible
member ([`security-and-access.md`](security-and-access.md)), but the named per-member coverage
is restricted to members of that SK. The gate is
`@ownerScopeService.canSeeJobOrderBlueprintOwners`, which evaluates the standard org-unit
scope predicate against the order's responsible org unit (so a non-admin matches only org
units in their own membership set, with no SK-public escape). A non-member who can otherwise
open a public SK order's detail page receives HTTP 403 from the endpoint, and the frontend
simply omits the section.

**Acceptance**

- [ ] A member of the responsible org unit receives the coverage view (HTTP 200).
- [ ] A non-member who can read a public SK order's detail receives HTTP 403 from the
  coverage endpoint, and the detail page renders without the section.
- [ ] An admin receives the coverage view for any order.

**Enforced by:** `OwnerScopeServiceTest` (canSeeJobOrderBlueprintOwners),
`JobOrderControllerTest` (getItemBlueprintOwners auth) · **Code:**
`OwnerScopeService.canSeeJobOrderBlueprintOwners` · **Issues:** —

## Out of scope

- Editing blueprint ownership from the order page — ownership is managed in the Personal
  Inventory area (#327); this view is read-only.
- The org-unit-wide blueprint-availability overview (#364) — a separate leadership oversight
  surface scoped to oversight org units, not to a single order's required products.
- Material orders, which request raw materials rather than crafted items and have no blueprint
  requirement.

## Open questions

None.
