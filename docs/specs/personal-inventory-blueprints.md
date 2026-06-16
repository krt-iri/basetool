> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-16.
> **Owner area:** INV/UI · **Related ADRs:** [ADR-0017](../adr/0017-default-blueprints-admin-curated-materialized.md)

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

**Code links:** [`DefaultBlueprintProvisioningService`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/DefaultBlueprintProvisioningService.java),
[`PersonalBlueprintRepository#grantDefaultBlueprintsToAllUsers`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/repository/PersonalBlueprintRepository.java),
[`UserService#syncUser`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/UserService.java),
[`DefaultBlueprintProvisioningTask`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/task/DefaultBlueprintProvisioningTask.java),
[`PersonalBlueprintService#requireRemovable`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/PersonalBlueprintService.java).

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

**Code links:** [`DefaultBlueprintService`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/DefaultBlueprintService.java),
[`AdminDefaultBlueprintController`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/AdminDefaultBlueprintController.java),
[`DefaultBlueprintBootstrap`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/DefaultBlueprintBootstrap.java),
[`V157__create_default_blueprint.sql`](../../backend/src/main/resources/db/migration/V157__create_default_blueprint.sql).
