> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-23.
> **Owner area:** ROLE · **Related ADRs:** ADR-0042 (qualifies ADR-0026, ADR-0027, ADR-0029)

# Functional rank & permission model (squadron / area / OL)

## Context & goal

The single flat Keycloak `OFFICER` role is no longer enough to model squadron leadership. Each
Staffel has exactly one **Staffelleiter**, up to four **Kommandoleiter** (each leading a
Kommandogruppe, optionally with a **stellvertretender Kommandoleiter**), and up to four **Ensigns**
(assigned to a Kommandogruppe or generally to the Staffelleitung). Each Bereich has one
**Bereichsleiter**, plus zero-or-more **Bereichskoordinatoren** and **Bereichsoperatoren**. Members
can belong to the **Organisationsleitung (OL)**; every Bereichsleiter is automatically part of the OL
body.

This spec defines the **functional rank model** — the source of truth for "what may this user do in
this org unit" — so that other features (first the bank, in a later session) can key permissions off
these ranks. It is the anchor for the rank-related amendments in
[`org-unit-tenancy.md`](org-unit-tenancy.md), [`security-and-access.md`](security-and-access.md) and
[`org-chart.md`](org-chart.md), and for the role matrix in
[`ROLES_AND_PERMISSIONS.md`](../../ROLES_AND_PERMISSIONS.md). Tracked by epic #800 (Phase 1 shipped:
the additive rank column + Kommandogruppe; Phases 2–5 wire authority, delegated assignment, UI and
the destructive cleanup).

## Requirements

### REQ-ROLE-001 — Unified membership rank enum

A single `@Enumerated(STRING)` `role` column on `org_unit_membership` (`MembershipRole`) is the
functional rank a user holds in an org unit. It supersedes the five mutually-exclusive boolean
leadership flags (`is_lead`, `is_bereichsleiter`, `is_bereichskoordinator`, `is_bereichsoperator`,
`is_ol_member`). Values: `MEMBER` (default), `STAFFELLEITER`, `KOMMANDOLEITER`,
`STELLV_KOMMANDOLEITER`, `ENSIGN`, `BEREICHSLEITER`, `BEREICHSKOORDINATOR`, `BEREICHSOPERATOR`,
`OL_MEMBER`, `SK_LEAD`. `is_logistician` / `is_mission_manager` stay as **orthogonal** capability
flags — they are NOT folded into the rank.

The rank is kind-scoped by the DB CHECK `chk_org_unit_membership_role_kind`: squadron ranks only on
`SQUADRON` rows, area ranks only on `BEREICH`, `OL_MEMBER` only on `ORGANISATIONSLEITUNG`, `SK_LEAD`
only on `SPECIAL_COMMAND`; `MEMBER` on any kind. During the additive soak (Phase 1) the boolean
columns remain authoritative and the rank is written in lockstep; the booleans drop in the Phase-5
cleanup.

**Acceptance**

- [x] `org_unit_membership.role` exists, defaults `MEMBER`, and is kind-scoped by CHECK (V184).
- [x] The rank is backfilled 1:1 from the five booleans (mutually exclusive ⇒ unambiguous).
- [ ](Phase 2) The authorisation layer reads the rank instead of the booleans, behaviour-identical
  for every existing area/OL/SK row.
- [ ](Phase 5) The five boolean columns are dropped once no code reads them.

**Enforced by:** `OrgHierarchyMigrationTest` (V184), `MembershipRoleMigrationEquivalenceTest` _(planned, Phase 2)_ · **Code:** `MembershipRole`, `OrgUnitMembership#role`, `V184__add_org_unit_membership_role_and_backfill.sql` · **Decision:** ADR-0042 · **Issues:** #800

### REQ-ROLE-002 — Baseline grant for squadron leadership ranks

Each squadron leadership rank (`STAFFELLEITER` / `KOMMANDOLEITER` / `STELLV_KOMMANDOLEITER` /
`ENSIGN`) confers officer-equivalent reach over its **own squadron only**, by minting contextual
`LOGISTICIAN@<squadronId>` + `MISSION_MANAGER@<squadronId>` exactly as `SK_LEAD` does for an SK —
**not** flat `ROLE_OFFICER`, **not** promotion rights, **not** cascade beyond the squadron. Area
ranks keep their current cascading reach (Bereich + children, identical across the three area ranks
for now). The Keycloak `OFFICER` realm role is left untouched. Per-rank / per-feature
differentiation (including the bank) is deferred to later work.

**Acceptance**

- [ ](Phase 2) A squadron leadership rank mints own-squadron logistician + mission-manager
  authorities and nothing else; area/OL/SK grants are byte-identical to before the migration.

**Enforced by:** `CustomJwtGrantedAuthoritiesConverterTest`, `OwnerScopeServiceTest` _(planned, Phase 2)_ · **Code:** `CustomJwtGrantedAuthoritiesConverter`, `OrgUnitCascadeService`, `OwnerScopeService` · **Decision:** ADR-0042 · **Issues:** #800

### REQ-ROLE-003 — Kommandogruppe entity

A Kommandogruppe is a first-class named sub-structure of a Staffel (`kommando_group`: squadron
`org_unit_id` FK, name, sort index, `@Version`); at most four per squadron. A membership with role
`KOMMANDOLEITER` / `STELLV_KOMMANDOLEITER` / `ENSIGN` carries a nullable `kommando_group_id`:
`KOMMANDOLEITER` / `STELLV_KOMMANDOLEITER` MUST reference a group; `ENSIGN` MAY (null = "allgemein
der Staffelleitung"); every other rank must have a null group. A group's parent must be a `SQUADRON`.

**Acceptance**

- [x] `kommando_group` exists, caps at four per squadron, and rejects a non-`SQUADRON` parent (V185).
- [x] `chk_org_unit_membership_kommando_group_role` confines `kommando_group_id` to the in-group
  squadron ranks (V185).
- [ ](Phase 3) Kommandogruppe CRUD + member assignment honour ≤1 KL and ≤1 stellv. per group and ≤4
  Ensigns per squadron.

**Enforced by:** `OrgHierarchyMigrationTest` (V185), `KommandoGroupServiceTest` _(planned, Phase 3)_ · **Code:** `KommandoGroup`, `KommandoGroupRepository`, `V185__create_kommando_group.sql` · **Decision:** ADR-0042 · **Issues:** #800

### REQ-ROLE-004 — Delegated appointment ladder, no self-promotion

Rank assignment is delegated down a strict ladder, with admin able to do everything:

- Admin → OL members (pure `OL_MEMBER`);
- OL members → Bereichsleiter;
- Bereichsleiter → Staffelleiter + SK-Lead + Bereichskoordinator + Bereichsoperator, for units in
  their own Bereich;
- Staffelleiter → Kommandoleiter + stellv. Kommandoleiter + Ensign + Kommandogruppen, for their own
  squadron.

Invariant: a leader can **never** assign the rank they themselves hold at that level (to grant rank
R you must hold a strictly-higher rank, which you cannot grant to yourself) — preserving the
"`is_lead` toggle is ADMIN-only" no-self-escalation property at every tier. The delegated verdict is
computed from the caller's own membership ranks only (never from the admin-pin header, contextual
authorities, or `isAdmin()` inside the verdict); admin short-circuits only at the `@PreAuthorize`
layer.

**Acceptance**

- [ ](Phase 3) Each tier can appoint exactly the rung below it, within its own scope; a foreign-unit
  or same-tier appointment is denied; the verdict never satisfies `isAdmin()`.

**Enforced by:** `OrgRoleManagementSecurityServiceTest`, `DelegatedAppointmentControllerSecurityTest` _(planned, Phase 3)_ · **Code:** `OrgRoleManagementSecurityService` _(planned)_ · **Decision:** ADR-0042 · **Issues:** #800

### REQ-ROLE-005 — Bereichsleiter auto-OL membership is organisational only

A Bereichsleiter is part of the OL body (appears in OL contexts and counts for OL governance /
appointments), but their **rights stay Bereich + children** (the strict-silo decision from epic #692,
REQ-ORG-015). Only a **pure** `OL_MEMBER` row confers org-wide reach. `OrgUnitCascadeService` is NOT
widened for a Bereichsleiter.

**Acceptance**

- [ ](Phase 2) A Bereichsleiter's reach is its Bereich + children, never org-wide; a pure OL member
  reaches every org unit.

**Enforced by:** `OrgUnitCascadeServiceTest`, `OwnerScopeServiceTest` _(planned, Phase 2)_ · **Code:** `OrgUnitCascadeService` · **Decision:** ADR-0042 · **Issues:** #800

### REQ-ROLE-006 — Roles are the source of truth; the org chart mirrors

The functional rank on `org_unit_membership` is the source of truth. The org chart
(`org_chart_position`) only **mirrors** account-linked seats descriptively and grants nothing
(REQ-ORG-010); free-text / account-less chart holders stay chart-only. Account-linked chart seats are
derived from the ranks; the authority cascade never reads the chart.

**Acceptance**

- [ ](Phase 4) Granting / revoking a rank updates the account-linked chart seat in the same
  transaction; the chart still grants nothing and the ArchUnit chart pins stay green.

**Enforced by:** `ArchitectureTest`, `OrgChartServiceTest` _(planned, Phase 4)_ · **Code:** `OrgChartService` · **Decision:** ADR-0042 · **Issues:** #800

## Out of scope

- **Bank** consumption of these ranks (who sees / requests on which account) — a later session; the
  `OrgUnitBankAccessService` / `OwnerScopeService` seam is kept clean for it.
- Per-rank / per-feature differentiation of rights beyond the SK-lead-equivalent baseline.
- Retiring the flat Keycloak `OFFICER` role for squadrons.

## Open questions

None outstanding — the six owner decisions (D1–D6) that shaped this spec are recorded in ADR-0042.
