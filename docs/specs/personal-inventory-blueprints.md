> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-21.
> **Owner area:** INV/UI · **Related ADRs:** [ADR-0017](../adr/0017-default-blueprints-admin-curated-materialized.md), [ADR-0024](../adr/0024-opt-in-global-blueprint-sharing.md), [ADR-0033](../adr/0033-blueprint-craftability-from-own-stock.md)

# Personal inventory — "Meine Blueprints" master-detail (V3)

## Context & goal

The personal blueprint collection (`/personal-inventory/blueprints`) uses the **master-detail
layout (V3)** approved in the design system. Binding visual sources: the V3 block of
`.claude/skills/das-kartell-design/proposals/blueprints-page-varianten.html` and the canonised
components in `krt-components.css` (`.master-detail`, `.master-list`, `.master-row(.is-active)`,
`.detail-pane`, `.quality-block`/`.quality-row`/`.quality-affects`, `.empty-state`, `.tab-nav`,
`.krt-modal*`).

The rebuild is a pure presentation restructure: every endpoint, form, and behaviour of the
previous table layout is preserved.

## Requirements

### REQ-INV-008 — Master-detail structure, deeplink, and add bar

The page head keeps the greeting plus a facts subtitle ("N Blueprints · M mit Notiz") and carries
a compact **add bar**: the typeahead search (staged selections render as compact chips under the
bar; the "Hinzufügen" CTA stays disabled until something is staged) and the **JSON import**
button. The PI tabs (Items | Blueprints) render as a `.tab-nav` with a `.tab-count`.

The collection renders as `.master-detail`: left a filterable `.master-list` (one input doubles
as instant client-side row filter and — on submit — the existing `?q=` server filter), one
`.master-row` per blueprint (product name, ✎ marker when a note exists); right a permanent
`.detail-pane`. The active row syncs with a `?bp={id}` URL parameter (deeplink); ↑/↓/Home/End
move the selection (listbox semantics: `role="listbox"`/`option`, `aria-selected`). Empty
collection and no-selection states use `.empty-state`. On viewports ≤900px the layout collapses
to list → detail navigation with a back button in the pane.

### REQ-INV-009 — Detail pane on the existing lazy recipe endpoint, calculation unchanged

The detail pane shows product name, "Erhalten am" (UTC → local), note edit ✎ and remove 🗑 as
icon buttons (`title`+`aria-label`, reusing the existing modal flows), and **"Zutaten &
Stat-Beitrag nach Zutat-Qualität"** as one `.quality-block` per requirement group from the
existing lazy endpoint (`GET /personal-inventory/blueprints/{id}/recipe`, cached per id): source
slot, material + quantity (SCU/units) + min. quality, ONE quality slider spanning the group's
effective band (`aria-label` "Qualität {material}", `aria-valuetext`), and the group's affected
stats as chips with **live factors**. The factor interpolation is the pre-existing
implementation, kept verbatim (`computeModifierValue`: ordered segments — interpolated when
`linear`, held constant for stepped forms — else linear interpolation across the effective
band); the mock's `1 + (max−1)·q/1000` formula is explicitly NOT used. Slider state is
view-only (no persistence). Blueprints without requirement groups fall back to the flat
ingredient list with a dash. The note renders below the recipe only when present.

### REQ-INV-010 — No regression of functions

All previous functions stay: typeahead search + staging + batch add; JSON import (file pick,
preview modal with Alle/Keine/Anwenden, per-row search/notes); `?q=` server filter; note edit
modal (optimistic-locking `version`, hidden `acquiredAt`, maxlength 2000); remove confirm
(danger `.krt-modal--danger` with `.btn-danger`, naming the product); per-blueprint recipe data;
UTC→local "Erhalten am"; CSRF hidden inputs; CSP nonces. Modals use the `.krt-modal*` frame
(one filled CTA, ghost cancel, Esc).

### REQ-INV-011 — Items page on the shared DS components

The personal inventory **items** page (`/personal-inventory`) uses the same shell as the
blueprints page: the greeting head carries a facts subtitle ("N Einträge", total element
count), the PI tabs render as the shared `.tab-nav` (Items active with a `.tab-count`,
Blueprints plain), an empty collection (optionally filtered via `?q=`) renders as an
`.empty-state` instead of an empty table row, and both modals (create/edit form, delete
confirm) use the `.krt-modal*` frame — the delete confirm as `.krt-modal--danger` with a
`.btn-danger` CTA naming the entry. Everything else is unchanged: action bar (create CTA +
`?q=` filter form), table columns, per-row edit/delete icon buttons with the
optimistic-locking `data-version` payload, UEX location typeahead, quantity sanitizing,
CSRF hidden inputs, CSP nonces, and the inline re-render of the form modal on validation
errors (`showItemModal`).

### REQ-INV-016 — Default blueprints are auto-granted to every user and non-removable

A small set of crafting blueprints is unlocked by default on every Star Citizen account (the
starter pistol, rifle, their magazines, and the Field Recon Suit pieces). These can no longer be
earned in-game and therefore never appear in an SCMDB / Basetool Blueprint Extractor import, so the
basetool MUST grant them itself.

The system MUST maintain, for **every** user, an owned `personal_blueprint` row for each entry in
the admin-managed default set (REQ-INV-017). Provisioning MUST be idempotent and MUST cover both
existing and future users:

- a brand-new user receives the defaults synchronously when their `app_user` row is first created
  (the grant runs in the same `UserService.syncUser` transaction, so the rows are committed before
  the first request returns);
- a deploy / drift is reconciled by a startup backfill and a periodic sweep
  (`DefaultBlueprintProvisioningTask`), both bulk `INSERT … SELECT … ON CONFLICT (owner_sub,
  product_key) DO NOTHING` so a re-run never duplicates;
- adding a new default grants it to all existing users at once (REQ-INV-017).

Granting MUST use the same normalized `product_key` the rest of the blueprint feature uses, so a
granted default lines up with the catalog, the product search "owned" flag, the availability
overview (REQ-INV-012/013) and the job-order coverage view — i.e. a default counts as genuinely
owned everywhere, for every user.

A default blueprint MUST NOT be removable by the user: the personal-blueprint list **hides** the
delete control for it (driven by a non-visible `removable` flag on the response, never a visible
badge), and the delete endpoint MUST refuse a default product server-side
(`error.personalBlueprint.defaultNotRemovable` → 409). Removing a product from the default set
(REQ-INV-017) does NOT revoke the rows users already hold — they simply become ordinary, removable
owned blueprints. Provisioning can be disabled per environment via
`APP_DEFAULT_BLUEPRINTS_PROVISIONING_ENABLED`; the sweep interval is
`APP_DEFAULT_BLUEPRINTS_PROVISIONING_INTERVAL` (default `PT1H`).

**Acceptance criteria:**

- [ ] Given a user with no default blueprints, when provisioning runs, then a `personal_blueprint`
  row exists for each default and a re-run inserts nothing further.
- [ ] Given a default blueprint in a user's list, then the list renders no delete control for it,
  and a direct `DELETE` of it returns 409 with code `BUSINESS_CONFLICT`.
- [ ] Given an admin removes a product from the default set, then users keep the rows they already
  hold and those rows become removable.

**Code links:** [`DefaultBlueprintProvisioningService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/DefaultBlueprintProvisioningService.java),
[`PersonalBlueprintRepository#grantDefaultBlueprintsToAllUsers`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/repository/PersonalBlueprintRepository.java),
[`UserService#syncUser`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/UserService.java),
[`DefaultBlueprintProvisioningTask`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/task/DefaultBlueprintProvisioningTask.java),
[`PersonalBlueprintService#requireRemovable`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/PersonalBlueprintService.java).

### REQ-INV-017 — Admin curation of the default-blueprint set

The default set lives in the `default_blueprint` table and is curated by administrators, so it can
follow CIG's starter loadout without a code deploy. The set is **seeded once** on first boot from a
curated starter list (`DefaultBlueprintCatalog`), guarded by a `defaultBlueprints.seeded`
`SystemSetting` flag so a later admin removal is never resurrected; each starter name is resolved
against the live blueprint catalog to stamp the canonical key / name / output item, and an
unresolved name is still seeded (degraded) so the default is granted regardless.

Administrators MUST be able to list the set, add a product (picked from the blueprint catalog
type-ahead, resolved to a `product_key` + name + output item), and remove an entry, through an
ADMIN-gated surface (`/api/v1/admin/default-blueprints`, `@PreAuthorize("hasRole('ADMIN')")`). A
duplicate add MUST return 409. Adding MUST immediately grant the new default to all existing users
(REQ-INV-016).

**Acceptance criteria:**

- [ ] Given a fresh database with the catalog populated, when the app boots, then the starter
  defaults are seeded once and granted to all users, and a reboot does not re-seed.
- [ ] Given an admin adds a catalog product as a default, then it appears in the set and every user
  gains the owned row; adding the same product again returns 409.
- [ ] Given a non-admin calls the default-blueprint admin endpoints, then the request is rejected
  with 403.

**Code links:** [`DefaultBlueprintService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/DefaultBlueprintService.java),
[`AdminDefaultBlueprintController`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/controller/AdminDefaultBlueprintController.java),
[`DefaultBlueprintBootstrap`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/DefaultBlueprintBootstrap.java),
[`V157__create_default_blueprint.sql`](../../backend/src/main/resources/db/migration/V157__create_default_blueprint.sql).

### REQ-INV-018 — Opt-in global blueprint sharing

By default a user's owned `personal_blueprint` rows count toward the leadership
blueprint-availability overview (REQ-INV-012) and the item-order blueprint-coverage view
(REQ-ORDERS-015) **only for the org units the user is a member of**. A user MAY opt in, via a
toggle on their profile page, to share their blueprints **globally**: while
`app_user.share_blueprints_globally` is `true`, the user is counted in **both** views for
**every** org unit, even when org-unit membership would otherwise exclude them — so a Staffel
member's blueprint can satisfy an SK order's coverage across org-unit boundaries.

This is a deliberate, user-controlled carve-out to the org-unit scoping ([ADR-0024](../adr/0024-opt-in-global-blueprint-sharing.md));
it preserves the surrounding guarantees:

- **Opt-in only**, default off (`NOT NULL DEFAULT FALSE`); nothing changes for users who do not
  enable it.
- It widens **whose** blueprints are counted, never **who may open** the views — the
  viewer-access gates (`OwnerScopeService.canAccessBlueprintOverview`,
  `canSeeJobOrderBlueprintOwners`) are unchanged, so a non-member still cannot open either view.
- **Read-only** exposure by **display name only** (`User.getEffectiveName()`, never the `sub` or
  e-mail); it grants no edit rights on the owner's blueprints.
- In both views, an owner shown **only** via this opt-in (not a member of the relevant org unit) is
  marked with a **discreet "not a unit member" hint**, so leadership can tell a cross-unit volunteer
  apart from their own members at a glance. In the admin "all org units" overview scope — where no
  single unit applies — no owner is marked.

The flag is self-service: the user reads / sets it through `GET` / `PUT
/api/v1/users/me/blueprint-sharing` (JWT-scoped, optimistic-locked), saved in place on the
profile page (REQ-FE-001) next to the payout preference. The two aggregations union the opted-in
users' `owner_sub`s into their org-unit member set before counting; an owner who is both a member
and a global sharer is counted once.

**Acceptance criteria:**

- [ ] Given a user enables the toggle and owns a required blueprint, when the coverage view of an
  order whose responsible org unit they are **not** a member of is built, then they are counted
  and appear as an owner row.
- [ ] Given the same user, then they appear in the availability overview of a leadership viewer
  who oversees an org unit the user does not belong to.
- [ ] Given the user disables the toggle, then they are removed from both views again.
- [ ] The opt-in never lets a viewer open a view they could not already open (the access gates
  are unchanged), and the owner is exposed by display name only.
- [ ] An owner shown only via global sharing is rendered with a discreet "not a unit member"
  marker in both the availability drill-down and the order-coverage view; a genuine member of the
  relevant unit is not marked.
- [ ] Given a user who never opts in, then they are counted only in their own org units
  (unchanged behaviour).

**Code links:** [`UserService#updateUserShareBlueprintsGlobally`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/UserService.java),
[`UserController`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/controller/UserController.java) (`/me/blueprint-sharing`),
[`PersonalBlueprintOverviewService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/PersonalBlueprintOverviewService.java),
[`JobOrderItemBlueprintOwnersService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/JobOrderItemBlueprintOwnersService.java),
[`V163__add_share_blueprints_globally_to_user.sql`](../../backend/src/main/resources/db/migration/V163__add_share_blueprints_globally_to_user.sql).

### REQ-INV-019 — Craftability of own blueprints from "My Inventory" stock

The blueprint view annotates each of the caller's owned blueprints with whether and how many times
it can be crafted **right now** from the caller's own stock, the output stats that stock's quality
would deliver, and what is missing — answering "what can I craft now, how often, with what stats,
and what's short?" without opening every recipe by hand ([ADR-0033](../adr/0033-blueprint-craftability-from-own-stock.md), issue #781).

It is **strictly owner-scoped** (JWT `sub`): owned blueprints, stock, and refinery yield all come
from the caller — no other user's data is ever read. It is **read-only** (no migration), computed
over existing data via `GET /api/v1/personal-blueprints/craftability?includeRefinery={false|true}`.

- **Stock source.** The caller's "My Inventory" / "Mein Lager" entries (`InventoryItem` where
  `user == me`, i.e. the `/inventory/my` set — both personal and shared rows the user owns), pooled
  **per material across all storage locations**. This is NOT the free-text personal-inventory
  feature; matching is by the real `Material` FK, so it is exact.
- **Ingredient scope.** Only **RESOURCE** (commodity) ingredients are evaluated. A recipe that also
  needs **ITEM** ingredients still shows, with the ITEM requirement marked "not evaluated"; an
  ITEM-only / unresolved recipe is reported as not assessable.
- **Qualifying stock.** Only stock at or above a per-material **quality floor** counts toward
  availability **and** the effective quality. The floor is the stricter of (a) the ingredient's
  `min_quality` and (b) the **no-degradation floor**: the lowest quality at which none of the slot's
  stat modifiers would *worsen* the output. Worsening is defined on the multiplier (the only datum
  available — there is no absolute base stat): below neutral (×1.0) for a `higher`-is-better stat,
  above neutral for a `lower`-is-better stat. A modifier that worsens across its entire band imposes
  no floor (it is treated as inherently penalised and ignored) so a recipe never silently becomes
  uncraftable.
- **Craftable count.** Per material the recipe's RESOURCE lines are aggregated into one required SCU
  per craft; `N = floor( min over materials of ( qualifying available SCU / required SCU ) )`. The
  binding ("limiting") material is named.
- **Effective quality & projected stats.** The effective ingredient quality is the **SCU-weighted
  average of the best-quality qualifying stock consumed first**, over one craft's requirement. Each
  build slot's stat slider in the detail pane **defaults to** that effective quality (instead of the
  band maximum), so the chips show the stats the user's own material would actually deliver; the
  slider still lets the user explore other qualities. Stat projection reuses the existing modifier
  model verbatim (`computeModifierValue`: ordered segments honoured, else linear across the band).
- **Refinery fold-in.** A view toggle, **default OFF**, folds in the yield of the caller's own
  `OPEN` + `IN_PROGRESS` refinery orders ("not yet completed or cancelled"); the refinery yield's
  quality participates in the effective-quality calculation. Every figure is produced **twice**
  (inventory alone, and with refinery), so the toggle switches client-side without a refetch. A
  blueprint craftable only thanks to the refinery is marked (`⟢`).
- **UI.** The master-list row carries a craft-status badge (`×N` craftable / `fehlt` not craftable /
  `×N ⟢` refinery-only); the detail pane shows the craftable counter, the limiting material, and a
  per-material consumption / shortfall breakdown. Follows the DAS KARTELL master-detail design
  (status-green / warning-yellow / research-blue accents, orange reserved for the add CTA),
  responsive across all four device classes, all strings via i18n (de + en + fallback).

**Acceptance criteria:**

- [ ] Given the caller owns a blueprint and enough qualifying RESOURCE stock, then the row and
  detail show `×N` with `N = floor(min over materials of available/required SCU)`, pooled across
  all locations.
- [ ] Given a material has stock spread over several quality tiers, then the reported effective
  quality is the SCU-weighted average of the best-quality stock consumed first over one craft, and
  the slot slider defaults to it.
- [ ] Given stock below an ingredient's `min_quality` or below the no-degradation floor, then that
  stock does not count toward availability or the effective quality.
- [ ] Given a not-fully-craftable blueprint, then the detail lists the short materials and the
  shortfall in SCU and the row shows "fehlt".
- [ ] Given the refinery toggle is on, then the caller's `OPEN` + `IN_PROGRESS` refinery yield is
  added (quantity and quality), counts are recomputed, and a blueprint craftable only via refinery
  is marked `⟢`.
- [ ] Given a recipe with ITEM ingredients, then it still displays and the ITEM requirement is
  marked "not evaluated"; an ITEM-only recipe reports as not assessable.
- [ ] Everything is owner-scoped by JWT `sub`; no other user's blueprints, stock, or refinery
  orders are visible.

**Code links:** [`BlueprintCraftabilityService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/BlueprintCraftabilityService.java),
[`BlueprintModifierMath`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/BlueprintModifierMath.java),
[`PersonalBlueprintController#craftability`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/controller/PersonalBlueprintController.java),
[`InventoryItemService#getOwnedStockSlices`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/InventoryItemService.java),
[`RefineryOrderService#getOwnedOpenRefineryYieldSlices`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/RefineryOrderService.java),
[`BlueprintCraftabilityDto`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/model/dto/BlueprintCraftabilityDto.java),
[`PersonalInventoryBlueprintsPageController#craftability`](../../frontend/src/main/java/de/greluc/krt/profit/basetool/frontend/controller/PersonalInventoryBlueprintsPageController.java),
[`personal-inventory-blueprints-recipe.js`](../../frontend/src/main/resources/static/js/personal-inventory-blueprints-recipe.js).
