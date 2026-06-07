> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-07.
> **Owner area:** ORG · **Related:** [`security-and-access.md`](security-and-access.md) · issues #214, #340–#344

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

Use [`OwnerScopeService.currentOrgUnitId()`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/OwnerScopeService.java)
for list-endpoint filters (three-parameter `ScopePredicate`: `boolean isAdminAllScope`,
`UUID activeOrgUnitId`, `Set<UUID> memberOrgUnitIds`) and `OwnerScopeService.canSee*` /
`canEdit*` for `@PreAuthorize` SpEL on detail/write endpoints. Admins without an active pin
get all-scope visibility; admins with a pin get the same restrictive view as a member;
non-admins see the union of their memberships unless they pin one.

### REQ-ORG-003 — Aggregate scope kinds

- **Strict-staffel** (no cross-staffel escape): `Ship`, `InventoryItem` (direct Lager-View),
  `RefineryOrder`, `Operation`. List/CRUD filter by `owning_org_unit_id`; detail gates on
  `canSee*`/`canEdit*`. `InventoryItem` rows are append-only and collapsed into display
  *stacks* only at read time (see [`inventory-lager.md`](inventory-lager.md), `REQ-INV-*`);
  the grouping is a display concern keyed within a single `owning_org_unit_id` pool and never
  widens this scope.
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

- `resolveOrgUnitForPickerOutput` (strict) → **400** for a membershipless user. Used by
  aggregates that are org-owned by construction with no creator-owner fallback (`Operation`).
- `resolveOrgUnitForPickerOutputNullable` → **null** (ownerless) for a membershipless user who
  supplied no picker output; a non-null *foreign* pick still 400s. Used by the owner-carrying
  aggregates that may legitimately exist without an OrgUnit: `Ship`, `RefineryOrder`,
  `InventoryItem` (V132) and `Mission` (V144 — see REQ-ORG-009).

Job Order uses its own resolvers: `responsible_org_unit_id` must be profit-eligible (or the configured intake SK
from system setting `job_order.intake_special_command_id` for guest creations);
`requesting_org_unit_id` accepts any OrgUnit and is freely editable.

### REQ-ORG-005 — Admin area & promotion carve-outs

Admin area is admin-only (post-Phase-4 lockdown), with carve-outs: Stammdaten,
user-management, announcement writes, system settings and **SK lifecycle**
(`/admin/special-commands`) are `hasRole('ADMIN')`. SK **member management** is open to ADMIN
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

[`ArchitectureTest`](../../backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java)
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

### REQ-ORG-009 — Ownerless leadership ("Bereichsleitung") missions

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
ownerless mission surfaces in the lists of members-or-above. Only `Mission` gains this carve-out;
`Operation` and `JobOrder` stay NOT NULL (org-owned by construction, no creator-owner fallback).
