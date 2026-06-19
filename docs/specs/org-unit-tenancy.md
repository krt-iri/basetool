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
