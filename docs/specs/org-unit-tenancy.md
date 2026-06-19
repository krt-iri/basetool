> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-19.
> **Owner area:** ORG · **Related:** [`security-and-access.md`](security-and-access.md) · issues #214, #340–#344, #500

# Multi-org-unit tenancy & scope (CRITICAL)

## Context & goal

The system supports multiple OrgUnits in parallel. Two kinds coexist under a shared
`org_unit` table with a `kind` discriminator: `SQUADRON` (the legacy Staffel) and
`SPECIAL_COMMAND` (SK). Scope (who can see/edit what) is enforced in the service layer.

## Requirements

### REQ-ORG-001 — OrgUnit model & dual-write soak

Two kinds under `org_unit.kind`: `SQUADRON` and `SPECIAL_COMMAND`. The IRIDIUM Squadron is
the canonical UUID `00000000-0000-0000-0000-000000000001`. During the dual-write soak every
staffel-scoped aggregate carries both the legacy `owning_squadron_id` and the new
`owning_org_unit_id`, kept in lockstep by JPA `@PrePersist`/`@PreUpdate`/`@PostLoad` hooks.
Repository queries read the new column; the legacy column drops in the destructive cleanup
release.

> **Amended by epic #692 (REQ-ORG-014):** `org_unit.kind` gains `BEREICH` and `ORGANISATIONSLEITUNG`, and
> a nullable self-referential `parent_org_unit_id` introduces the fixed three-level hierarchy
> OL → Bereich → Staffel/SK.

### REQ-ORG-002 — Scope is enforced in the service layer

Use [`OwnerScopeService.currentOrgUnitId()`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/OwnerScopeService.java)
for list-endpoint filters (three-parameter `ScopePredicate`: `boolean isAdminAllScope`,
`UUID activeOrgUnitId`, `Set<UUID> memberOrgUnitIds`) and `OwnerScopeService.canSee*` /
`canEdit*` for `@PreAuthorize` SpEL on detail/write endpoints. Admins without an active pin
get all-scope visibility; admins with a pin get the same restrictive view as a member;
non-admins see the union of their memberships unless they pin one.

### REQ-ORG-003 — Aggregate scope kinds

- **Strict-staffel** (no cross-staffel escape): `Ship`, `InventoryItem` (direct Lager-View),
  `RefineryOrder`. List/CRUD filter by `owning_org_unit_id`; detail gates on
  `canSee*`/`canEdit*`. `InventoryItem` rows are append-only and collapsed into display
  *stacks* only at read time (see [`inventory-lager.md`](inventory-lager.md), `REQ-INV-*`);
  the grouping is a display concern keyed within a single `owning_org_unit_id` pool and never
  widens this scope. **All three of these personal aggregates (`Ship`, `InventoryItem`,
  `RefineryOrder`) carry the per-user owner escape of REQ-ORG-011** — the row's own owner
  (`ship.owner` / `inventory_item.user` / `refinery_order.owner`) may always see/edit it regardless
  of the `owning_org_unit_id` stamp; this is a per-owner detail/edit carve-out only and does not
  widen the shared Lager-/Hangar-/refinery views. A non-owner stays strictly scoped.
- **Strict-staffel with two read-only escapes:** `Operation` — owned by a Staffel/SK and filtered
  by `owning_org_unit_id` like the strict aggregates, but visible beyond the owning scope in two
  cases (view only; editing stays role+scope via `canEditOperation`): (1) an **ownerless** operation
  (`owning_org_unit_id IS NULL`, V145) is a leadership/"Bereichsleitung" operation visible to
  organisation members-or-above (`viewerIsMemberOrAbove`; no public escape — operations are never
  anonymous-visible); (2) any authenticated user who **participated** in one of the operation's
  linked missions may see it (`viewerUserId` matches a `mission_participant.user_id` of a mission
  whose `operation_id` is this operation), so participants can view the operation and their payout
  regardless of owning Staffel. Both escapes are gated in the service layer and mirrored across the
  three scoped queries (`findAllScoped`, `findAllReferenceScoped`, `searchOperations`) and
  `canSeeOperation`. See REQ-ORG-009.
- **Cross-staffel with public escape:** `Mission` — visible to other OrgUnits iff
  `is_internal = false`; editable only by the owning OrgUnit + admins (`searchMissions`
  enforces `owning_org_unit.id IN (:memberOrgUnitIds) OR is_internal = false`). A mission may
  also be **ownerless** (`owning_org_unit_id IS NULL`, V144) — a leadership / "Bereichsleitung"
  mission created by a user who belongs to no OrgUnit; see REQ-ORG-009.
- **Conditionally staffel-scoped** (visibility driven by the responsible OrgUnit's `kind`,

  # 343): `JobOrder` + linked `JobOrderMaterial` + `JobOrderHandover`. Job Order carries

  `responsible_org_unit_id` (the **processing** unit — a profit-eligible squadron or SK;
  governs visibility; mutable only via `PATCH /api/v1/orders/{id}/responsible-org-unit`) and
  `requesting_org_unit_id` (the **customer**; any active OrgUnit; does NOT grant visibility).
  Responsible = SK → public to all squadrons (shared SK queue); responsible = Squadron →
  private to that squadron + admins. `findScopedJobOrders` adds the SK-public escape
  `TYPE(responsibleOrgUnit) = SpecialCommand`. SK-order *edits* are governed by the role gate
  (LOGISTICIAN+), not by squadron scope. Inventory items linked via `job_order_id` surface
  cross-OrgUnit inside the order UI but NEVER leak into a foreign Lager-View
  (`findGlobalByFilters` is gated, `findByJobOrderIdOrdered` is not).

> **Amended by epic #692 (REQ-ORG-016):** `owning_org_unit_id` (and `responsible_org_unit_id`) may now
> reference a `BEREICH` or `ORGANISATIONSLEITUNG` org_unit. Such rows participate in **these same** scope
> kinds and escape rules unchanged — the only new behaviour is that the descendant cascade (REQ-ORG-015)
> lets a higher level see/edit its subordinate units' rows; a subordinate never sees the level above
> (strict silo).

### REQ-ORG-004 — Create-time OrgUnit stamping

Stamp the OrgUnit via the central picker resolvers on `OwnerScopeService` — never read
`user.getSquadron()` directly. Two variants share the §5.5.1 picker matrix (1 + no output →
auto-stamp; 1 + valid → honoured; 1 + foreign → 400; >1 + no output → 400 (force choice); >1 +
valid → honoured) and differ only on the **0-membership** row:

- `resolveOrgUnitForPickerOutput` (strict) → **400** for a membershipless user. Currently wired
  to no aggregate — retained as the strict counterpart of the nullable resolver (`Operation`
  moved off it in V145; `JobOrder` uses its own resolvers, see below).
- `resolveOrgUnitForPickerOutputNullable` → **null** (ownerless) for a membershipless user who
  supplied no picker output; a non-null *foreign* pick still 400s. Used by every aggregate that
  may legitimately exist without an OrgUnit: `Ship`, `RefineryOrder`, `InventoryItem` (V132),
  `Mission` (V144) and `Operation` (V145 — see REQ-ORG-009).

**Refinery-store output stamping (#596).** Storing a refinery order creates one `InventoryItem`
per output, owned by the receiving member (`RefineryOrderStoreItemDto.userId`, else the order
owner). The store dialog carries a per-item owning-OrgUnit picker whose options are the *receiver's*
memberships and whose default selection is the order's own `owningOrgUnit`; the chosen id rides
`RefineryOrderStoreItemDto.owningOrgUnitId` into `resolveOrgUnitForPickerOutputNullable`. This closes
the gap where a multi-membership receiver was hard-rejected with the `>1 + no output → 400` branch
because the form offered no choice — the picker is always shown so the receiver's OrgUnit pool is
explicit, while the inherited default keeps a same-OrgUnit self-store one-click.

Job Order uses its own resolvers: `responsible_org_unit_id` must be profit-eligible (or the configured intake SK
from system setting `job_order.intake_special_command_id` for guest creations);
`requesting_org_unit_id` accepts any OrgUnit and is freely editable.

> **Amended by epic #692 (REQ-ORG-016):** stamping **validation** widens to `(direct memberships ∪
> oversight descendants)` so a Bereichsleitung/OL can create on behalf of a subordinate Staffel/SK and
> own their own Bereich/OL data; the **auto-stamp** default and the `>1 → force a choice` rule stay keyed
> on **DIRECT** membership, so ordinary-member stamping is unchanged.

### REQ-ORG-005 — Admin area & promotion carve-outs

Admin area is admin-only (post-Phase-4 lockdown), with carve-outs: Stammdaten,
user-management, announcement writes, system settings and **SK lifecycle**
(`/admin/special-commands`) are `hasRole('ADMIN')`. **SK delete is a soft-delete
(deactivate): it flips `active=false`, never hard-deletes** — memberships and any aggregate
already owned by the SK survive; only the active-list dropdowns and owner picker hide the row.
It is reversible via the activate endpoint, and `includeInactive=true` (ADMIN-only) surfaces
deactivated rows with a reactivate control. The list page's per-row trash button confirms
through a KRT modal (no native `confirm()`, per [`ui-design-system.md`](ui-design-system.md))
before POSTing the deactivate. SK **member management** is open to ADMIN
or an `is_lead` user of that SK (`SpecialCommandSecurityService.canManageMembers`); the
Lead-toggle stays ADMIN-only (no self-escalation). **Promotion-system maintenance** is
re-opened to OFFICER under an org-unit-scope gate (`canEditSquadron(topic.owningSquadron.id)`).
Admins can toggle the promotion subsystem per Squadron
(`PATCH /api/v1/squadrons/{id}/promotion-enabled`); `OwnerScopeService.isPromotionFeatureEnabledForCurrentScope()`
short-circuits promotion services when OFF. **SKs can never participate in promotion** —
enforced at DB (V97 CHECK + V101 trigger `guard_promotion_topic_owner_kind`), app
(`SpecialCommand` entity), and type layer (ArchUnit
`promotionTopicOwningSquadronMustStayTypedSquadronNotOrgUnit`).

> **Amended by epic #692 (REQ-ORG-015):** membership in a Bereich's Bereichsleitung or the OL grants
> **officer-equivalent reach** over the subordinate units, **never admin rights** — every carve-out in
> this requirement stays `hasRole('ADMIN')`, and an OL/Bereich principal must never satisfy `isAdmin()`.
> Bereichsleitung inherits the OFFICER promotion-maintenance carve-out for the **Staffeln** in its
> Bereich (via the normal officer path, not a widened promotion scope); Bereich/OL never own promotion
> data and SKs stay permanently excluded.

### REQ-ORG-006 — ArchUnit guards for new staffel-scoped aggregates

[`ArchitectureTest`](../../backend/src/test/java/de/greluc/krt/profit/basetool/backend/ArchitectureTest.java)
breaks the build if a staffel-scoped service stops injecting `AuthHelperService` /
`OwnerScopeService` (`staffelScopedServicesMustWireOwnerScopeOrAuthHelper` — update the
whitelist when adding an aggregate), and `noNewJoinColumnReferencingSquadronIdOutsideGrandfatheredEntities`
prevents new `@JoinColumn(name = "squadron_id")` (only `User.squadron` +
`MissionParticipant.squadron` are grandfathered).

### REQ-ORG-007 — Audit MDC field

`CorrelationIdFilter` emits MDC `orgUnitId` on every request (legacy `squadronId` in
parallel for one release). Logback patterns must include `%X{orgUnitId}`. See
[`observability.md`](observability.md).

### REQ-ORG-008 — Active-context relay

The frontend sends `X-Active-Org-Unit-Id` (canonical) + legacy `X-Active-Squadron-Id` on
every outbound call; the backend reads the new name first. Session attribute (Redis-backed)
is `iridium.activeOrgUnitId` with legacy `iridium.activeSquadronId` mirrored for one release.
Both aliases drop in the cleanup release.

### REQ-ORG-009 — Ownerless leadership ("Bereichsleitung") missions & operations

Organisation leadership sits above every Staffel and SK and belongs to no OrgUnit, yet must be
able to plan org-wide missions. A mission created by such a membershipless user is stamped
`owning_org_unit_id = NULL` (V144 relaxed the NOT NULL; the row stays attributable through
`mission.owner_id`) instead of being rejected with 400. Visibility layers on top of the
`owning_org_unit IS NULL` state, mirroring the Staffel-internal rule with the whole organisation
as the owning scope:

- **Public** (`is_internal = false`) → visible to everyone, anonymous visitors included (the
  create-time default).
- **Internal** (`is_internal = true`) → visible to organisation members-or-above
  (`AuthHelperService.isMemberOrAbove()`), hidden from guests/anonymous.

Editing follows the normal mission-management gate: `OwnerScopeService.canEditMission` is a no-op
(returns `true`) for an ownerless mission, so `MissionSecurityService.canManageMission` decides via
its usual elevated-role-or-owner/manager check (owner, co-managers, mission-managers/officers,
admins) — the same path as a normal mission, minus the squadron-scope narrowing. The list queries
(`searchMissions`, `findAllActiveReference`) carry a `viewerIsMemberOrAbove` flag so an internal
ownerless mission surfaces in the lists of members-or-above.

`Operation` gains the same carve-out (V145, #500) — `OperationService.createOperation` routes through
`resolveOrgUnitForPickerOutputNullable` — with two differences, because an operation has no per-user
creator column and no `is_internal` flag:

- **Attribution.** An ownerless operation has no `owner_id` to point at; it is attributable only as
  an organisation-wide leadership operation (audited via `created_at`/`updated_at`).
- **Visibility.** Operations have no public escape, so there is no public-vs-internal split: an
  ownerless operation is the org-wide analogue of a Staffel-internal operation — visible to
  organisation members-or-above (`AuthHelperService.isMemberOrAbove()`), hidden from guests/anonymous.
  `canSeeOperation` defers to `isMemberOrAbove()` on the null-owner branch, and the three scoped
  operation queries (`findAllScoped`, `findAllReferenceScoped`, `searchOperations`) carry a
  `viewerIsMemberOrAbove` flag so the list and the per-row detail gate stay consistent. Editing
  follows the role gate: `canEditOperation` is a no-op for an ownerless operation, so an org-wide
  operation is editable by any mission manager and deletable by any admin.
- **Participation — all operations (#500).** Independently of ownership, any *authenticated* user
  (member or guest, but never anonymous) who participated in one of an operation's linked missions
  may see that operation — so participants can view the operation and their payout even when it is
  owned by another Staffel or is ownerless. Implemented as a `viewerUserId` EXISTS branch on the
  three scoped queries plus a `canSeeOperation` participant check
  (`OperationRepository.existsParticipantUserInOperation`). This escape is view-only and never
  grants edit.

Only `Mission` and `Operation` gain this carve-out; `JobOrder` stays NOT NULL (org-owned by
construction, no creator-owner fallback and no ownerless-leadership use case).

> **Amended by epic #692 (REQ-ORG-016):** this ownerless (`owning_org_unit_id = NULL`) path is
> **preserved unchanged** — it remains the "org-wide, visible to members-or-above" semantic. It is
> distinct from Bereich/OL *ownership*: a `NULL` owner is org-wide, whereas a `BEREICH`/`ORGANISATIONSLEITUNG`
> owner scopes the row to that level's leadership (strict silo). No existing `NULL`-owner row is
> backfilled to a concrete owner.

### REQ-ORG-010 — Active-context surfacing in the UI

The active OrgUnit context is surfaced to the user **only** by appending it to the application
title (`appTitle`, resolved in
[`SquadronContextAdvice`](../../frontend/src/main/java/de/greluc/krt/profit/basetool/frontend/config/SquadronContextAdvice.java)),
which renders in both the browser `<title>` tag and the sidebar brand logo text. The suffix is:

- the active OrgUnit's **shorthand** (falling back to its name) for an active pin of **either**
  kind — `SQUADRON` *or* `SPECIAL_COMMAND`. `appTitle` reads the merged `activeOrgUnit` catalogue,
  not the Squadron-only `activeSquadron`, so an SK pin shows in the title;
- the localised "Alle Staffeln" label for an admin in all-OrgUnits mode (no pin);
- nothing (plain "Profit Basetool") when no context applies (squadron-less non-admin, anonymous).

There is **no separate context chip**. An always-on top-right context chip used to render the same
information (and was the only surface that distinguished/showed an SK pin); it was removed as
redundant once `appTitle` carried the context for every kind. Do not reintroduce a parallel
always-on context badge — the title is the single source. The Staffel-vs-SK *kind* distinction the
chip carried is intentionally not reproduced in the title; the shorthand identifies the unit.

### REQ-ORG-011 — Personal-aggregate owner retains see/edit across org-unit changes

The per-user owner of a **personal aggregate** — `InventoryItem` (`inventory_item.user`), `Ship`
(`ship.owner`) and `RefineryOrder` (`refinery_order.owner`) — may **always see and edit their own
row**, independent of the row's `owning_org_unit_id` stamp. This must hold in both boundary cases the
product owner called out:

- the owner **switched org units** — the row stays stamped to the former unit (a unit-switch is
  not a `NULL`↔non-`NULL` transition, so the [`InventoryOrgUnitReconciler`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/InventoryOrgUnitReconciler.java)
  does not re-stamp inventory, see `REQ-INV-004`; ships and refinery orders have no reconciler at
  all), so the owner is no longer a member of the row's owning unit;
- the owner **has no org unit at all** yet the row is **still stamped to one** (the demote to
  `NULL` has not happened / does not apply).

Enforced in
[`OwnerScopeService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/OwnerScopeService.java)
(`canSee/canEditInventoryItem`, `canSee/canEditShip`, `canSee/canEditRefineryOrder`): a shared
`isCurrentUserOwner(owner)` check runs **before** the strict `owning_org_unit_id` scope check (and
before the ownerless-row branch), so the `@PreAuthorize` gate can never deny a write the service
layer would accept. The service layers already authorise the owner with no org-unit narrowing —
`InventoryItemService` on every owner action (book-out, note, delivered toggle, association change,
bulk-checkout) `item.user == currentUser`; `HangarService.updateShip`/`deleteShip` reject any
non-owner; `RefineryOrderService.updateRefineryOrder`/`deleteRefineryOrder`/`storeRefineryOrder`
admit `owner == currentUser` (or a logistician). Before this, the gates delegated org-owned rows
straight to `canEditSquadron(owning_org_unit_id)`, which 403'd the owner the moment they left the
row's unit even though the service would have allowed the edit.

**Visibility surface is unchanged.** Each aggregate's personal view already lists the owner's rows
regardless of org unit (`/inventory/my` is a pure `inventory_item.user` filter; the Hangar and
refinery "my" lists are owner-scoped), exactly as ownerless rows already behave; the strict shared
views (`/inventory/all`, the squadron Hangar/refinery lists) still filter by `owning_org_unit_id`
and never surface a foreign-org row, so this carve-out does **not** widen cross-org visibility of the
shared pools. A **non-owner** stays bound by the strict `owning_org_unit_id` scope.

**Enforced by:** `OwnerScopeServiceTest` (`PersonalAggregateOwnerRetainsAccessTests`),
`InventoryTenancyE2eTest` (`ownerRetainsEditAfterLeavingOwningOrgUnit`) · **Code:**
`OwnerScopeService#canSeeInventoryItem` / `#canEditInventoryItem` / `#canSeeShip` / `#canEditShip` /
`#canSeeRefineryOrder` / `#canEditRefineryOrder`.

### REQ-ORG-014 — Three-tier org hierarchy (Organisationsleitung > Bereich > Staffel/SK)

The org unit world gains two levels **above** Staffel and SK, modelled on the existing single-table
`org_unit` (ADR-0025): two new `OrgUnitKind` values `BEREICH` (area/division, e.g. Profit, Sub-Radar,
Raumüberlegenheit) and `ORGANISATIONSLEITUNG` (OL), plus a nullable self-referential
`parent_org_unit_id`. The hierarchy is **fixed three levels**: OL → Bereich → (Staffel | SK). A CHECK
constraint pins the parent kind by the child kind (Staffel/SK parent is a `BEREICH`; Bereich parent is
the `ORGANISATIONSLEITUNG`; OL has no parent), which also forbids cycles at fixed depth.
`Bereich`/`Organisationsleitung` are JPA subclasses with promotion permanently disabled (like
`SpecialCommand`); `chk_org_unit_promotion_only_squadron` widens to keep promotion FALSE for both new
kinds. Roll-out is **additive** (mirror the V97–V100 soak): `parent_org_unit_id` is NULL until an admin
assigns Bereiche, and the descent helper (REQ-ORG-015) treats a NULL parent as "no ancestor expansion",
so the system is byte-identical to today's flat behaviour while the hierarchy is being populated.

**Acceptance**

- [ ] `OrgUnitKind` has `BEREICH` and `ORGANISATIONSLEITUNG`; `chk_org_unit_kind` and the discriminator
  accept them; `ddl-auto=validate` passes.
- [ ] `parent_org_unit_id` is nullable with the kind-pairing CHECK; no row can parent a kind other than
  the one its level allows; the OL row has a NULL parent.
- [ ] With every `parent_org_unit_id` NULL and no leadership flags set, `OwnerScopeService` scope output
  is byte-identical to pre-change (snapshot test).

**Enforced by (planned):** `OwnerScopeServiceTest` (degrade-to-flat snapshot), a migration test on a
prod-like snapshot, `ArchitectureTest` · **ADR:** [ADR-0025](../adr/0025-org-hierarchy-data-model.md) ·
**Issues:** #692, #694.

### REQ-ORG-015 — Cascading oversight without admin rights, via one descent helper

Leadership reach cascades, mirroring `ADMIN > OFFICER > LOGISTICIAN/MISSION_MANAGER`: a **Bereichsleitung**
(`is_bereichsleiter` / `is_bereichskoordinator` / `is_bereichsoperator`, REQ-ORG-017) gets
officer-equivalent reach over **all Staffeln + SKs of its Bereich** (and its Bereich's own data); the
**OL** (`is_ol_member`) over **everything**. This reach is computed in **exactly one** helper
`OwnerScopeService.expandWithDescendants(...)`, consumed by `currentMemberOrgUnitIds()` **and**
`currentBlueprintOversightScope()` (ADR-0026); `ScopePredicate.permits()` and the `IN :memberOrgUnitIds`
JPQL cascade automatically once fed the expanded set, so lists and per-row gates widen together.

Hard invariants:

- The reach is a **concrete `memberOrgUnitIds` union — never** the `adminAllScope=true` branch. An
  OL/Bereich principal **must never satisfy `isAdmin()`**, so all `hasRole('ADMIN')` carve-outs (SK
  lifecycle, system settings, stammdaten, the promotion-topic guards in REQ-ORG-005) stay ADMIN-only.
- **Strict silo:** a Bereichsleitung's union contains only its own Bereich's descendants; only the OL
  crosses Bereiche. No peer-Bereich visibility.
- An **SK-lead is not expanded** — they keep SK-only reach; their Bereichsleitung membership is
  organisational only (REQ-ORG-017).
- The JWT converter mints, per leadership membership, the flat `ROLE_LOGISTICIAN`/`ROLE_MISSION_MANAGER`
  (so `hasRole(...)` menu gates work) **and** a contextual `OrgUnitContextualAuthority(role, descendant)`
  per descendant (so existing `@PreAuthorize("@ownerScopeService.hasRoleInOrgUnit(#id, '…')")` resolves).
- The cascade **composes with** the personal-aggregate owner-bypass (REQ-ORG-011) and the opt-in global
  blueprint union ([ADR-0024](../adr/0024-opt-in-global-blueprint-sharing.md)); neither is weakened.

**Acceptance**

- [ ] For a Bereichsleitung and the OL, list-query scope equals `permits()` for every descendant **and**
  denies every non-descendant / foreign pin (foreign-pin-collapses-to-union preserved).
- [ ] A Bereichsleitung is denied another Bereich's data in both lists and detail gates (strict silo).
- [ ] An OL/Bereich principal fails `isAdmin()` and every `hasRole('ADMIN')` carve-out.
- [ ] An SK-lead's reach is SK-only (no Bereich expansion).
- [ ] The REQ-ORG-011 owner-bypass and the ADR-0024 global-sharing union still hold for a leadership
  principal.

**Enforced by (planned):** `OwnerScopeServiceTest`, `CustomJwtGrantedAuthoritiesConverterTest`,
visibility-matrix e2e · **ADR:** [ADR-0026](../adr/0026-cascading-scope-without-admin.md) · also pins
[REQ-SEC-015](security-and-access.md) · **Issues:** #692, #696.

### REQ-ORG-016 — Bereich/OL as direct owners of org-unit-scoped aggregates

A Bereich and the OL **own their own data** — `owning_org_unit_id` may reference a `BEREICH` or
`ORGANISATIONSLEITUNG` org_unit — in addition to overseeing subordinate units (ADR-0027). Leadership may
also **create on behalf of** a subordinate Staffel/SK. The create-time stamping matrix (REQ-ORG-004) is
extended:

- **Validation** of a picker choice widens to `(direct memberships ∪ oversight descendants)`: a
  Bereichsleitung may stamp its own Bereich or any descendant; the OL anything.
- **Auto-stamp stays on a single DIRECT membership** (a leader's default owner = their own Bereich/OL;
  `>1` direct forces an explicit pick; a foreign / out-of-oversight pick is a 400) — ordinary members'
  stamping is unchanged.
- **Create-on-behalf is authorised by `canEditOrgUnit(target)`** (cascades per REQ-ORG-015), not by
  admin-ness; every create/update path accepting a target owning-org-unit (incl.
  `JobOrderService.resolveResponsibleOrgUnit`) gains this gate.
- Visibility reuses the existing scope + public/internal escape rules unchanged; strict silo holds. The
  ownerless `NULL` leadership path (REQ-ORG-009) is **preserved unchanged** (no backfill). Promotion
  stays Squadron-only. The REQ-ORG-011 owner-bypass stays ahead of the scope check.

**Acceptance**

- [ ] A Bereich-owned and an OL-owned aggregate can be created, read and edited by that level's
  leadership; a subordinate cannot see the level above (strict silo).
- [ ] Create-on-behalf of a descendant succeeds; of a non-descendant fails (400/403).
- [ ] Ordinary-member and officer stamping is byte-identical to today.
- [ ] The ownerless `NULL` path behaves exactly as REQ-ORG-009.

**Enforced by (planned):** per-aggregate stamping/visibility tests, `OwnerScopeServiceTest`,
visibility-matrix e2e · **ADR:** [ADR-0027](../adr/0027-bereich-ol-aggregate-ownership.md) · **Issues:**

# 692, #697.

### REQ-ORG-017 — Membership cardinality & exclusivity rules

`org_unit_membership` gains leadership flags `is_bereichsleiter` / `is_bereichskoordinator` /
`is_bereichsoperator` (valid only on `BEREICH`-kind rows) and `is_ol_member` (only on
`ORGANISATIONSLEITUNG`-kind rows), each pinned to its kind by a CHECK (mirroring
`chk_org_unit_membership_lead_only_on_special_command`). The cardinality/exclusivity rules:

1. A member may belong to **up to two Staffeln** (possibly from different Bereiche) and **any number of
   SKs** — the V95 `uq_org_unit_membership_one_squadron` "≤1 squadron" guard relaxes to "≤2".
2. An **SK-lead** (`is_lead=true`) belongs to **no Staffel** and is **always** an (organisational,
   reach-less per REQ-ORG-015) member of the **Bereichsleitung of the Bereich its SK belongs to**.
   This Bereichsleitung membership is **derived, not stored**: it is computed from `is_lead` + the
   SK's `parent_org_unit_id` wherever it is needed (e.g. the org chart), so there is no separate
   membership row to keep in sync and no stale seat on demotion or SK re-parenting. (An admin may
   still grant such a user an *explicit, flagged* Bereichsleitung role via
   `POST /api/v1/org-hierarchy/bereiche/{id}/members` — that is an additive, reach-bearing grant,
   independent of the derived organisational seat.)
3. **Bereichsleitung members** (`is_bereichsleiter`/`koordinator`/`operator`) belong to **no Staffel**.
4. **OL members** (`is_ol_member`) belong to **no Staffel**, but **may** belong to a Bereich.
5. A user may hold leadership in **more than one** Bereich (the reach unions per REQ-ORG-015).

Rules 1 and the flag-kind pairing are expressible at the DB layer (a relaxed guard + CHECKs). The
cross-row rules (2–4: "leadership/OL/SK-lead hold no Staffel", "≤2 Staffeln") cannot be a single-row
CHECK and are enforced by a **trigger AND a service-layer guard** (defence in depth); raw-SQL/curl paths
must not be able to violate them. `membership.kind` and the trigger-synced flags stay
`insertable=false, updatable=false`.

**Acceptance**

- [ ] A third Staffel membership is rejected; a second is accepted.
- [ ] Making a user an SK-lead while they hold a Staffel membership is rejected; assigning an SK to a
  Bereich (or making the user lead) auto-adds the reach-less Bereichsleitung membership.
- [ ] A Bereichsleitung or OL flag on a user who holds a Staffel membership is rejected.
- [ ] A leadership flag on the wrong `kind` is rejected by CHECK.

**Enforced by (planned):** `OrgUnitMembershipServiceTest`, a DB-constraint/trigger test,
`ArchitectureTest` (validation-call rule) · **Issues:** #692, #695.
