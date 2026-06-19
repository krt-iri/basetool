> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-19.
> **Owner area:** MISSION · **Related ADRs:** none

# Home-page "next mission" banner

## Context & goal

The home page (`/`) shows a **next mission** banner: the single upcoming mission whose planned start
is the soonest still in the future. It is the viewer's at-a-glance "what is _my unit_ coming up
against". The banner is guest-visible — anonymous visitors see only public missions; authenticated
members also see internal ones.

The banner must surface only missions that are still **operationally relevant**. A mission that has
already been `COMPLETED` or `CANCELLED` but happens to carry a future planned-start time (e.g. a
cancelled future plan, or a closed mission whose schedule was never corrected) is not something the
squadron is heading towards, and showing it as "next mission" is misleading.

The banner must also be **org-unit relevant**. A member should see the next mission of their _own_
org unit (or, for a Bereich/Organisationsleitung leader, their subordinate units), not the
organisation-wide next mission that may belong to a squadron they have nothing to do with. A viewer
who belongs to no org unit (anonymous guest, brand-new account, admin in "all squadrons" mode) keeps
the organisation-wide next mission as before.

## Requirements

### REQ-MISSION-003 — Next-mission banner only considers PLANNED or ACTIVE missions

The home-page next-mission lookup considers a mission **eligible** only when its `status` is
`PLANNED` or `ACTIVE`. Among the eligible missions, the one with the soonest `plannedStartTime`
strictly after "now" is the next mission.

- A `COMPLETED` or `CANCELLED` mission is **never** the next mission, even if its `plannedStartTime`
  is in the future and earlier than every eligible mission's.
- The existing visibility rule is unchanged: guests (`allowInternal = false`) see only public
  (`isInternal = false`) missions; authenticated callers (`allowInternal = true`) also see internal
  ones.
- When no eligible mission is upcoming, the endpoint returns `204 No Content` and the page renders
  its empty state — unchanged.

**Acceptance**

- [ ] Given an upcoming `PLANNED` mission and an upcoming `ACTIVE` mission, the banner shows whichever has the earlier planned start.
- [ ] Given a `COMPLETED`/`CANCELLED` mission with an earlier future planned start and a later `PLANNED` mission, the banner shows the `PLANNED` mission — the terminal one is skipped, not merely sorted behind.
- [ ] A guest never sees an internal mission in the banner; an authenticated member does.
- [ ] When no `PLANNED`/`ACTIVE` mission is upcoming, the endpoint returns 204.

**Enforced by:** `MissionServiceTest` (`getNextMission_*` — status passed to the finder),
`MissionRepositoryLookupOrderingTest`
(`findFirstByPlannedStartTimeAfterAndStatusIn_skipsTerminalStatusEvenWhenItSortsEarlier`).
**Code:** `MissionService#getNextMission`,
`MissionRepository#findFirstByPlannedStartTimeAfterAndStatusInOrderByPlannedStartTimeAsc` +
`#findFirstByPlannedStartTimeAfterAndIsInternalFalseAndStatusInOrderByPlannedStartTimeAsc`,
`MissionController` `/api/v1/missions/next`, `frontend/.../HomeController` + `templates/index.html`.

### REQ-MISSION-008 — Next-mission banner is scoped to the viewer's org units

The next-mission lookup is restricted to missions **owned by the viewer's effective org units** when
the viewer has any:

- The effective scope is the same vector the scoped mission lists use
  (`OwnerScopeService#currentScopePredicate()`): a plain member's own Staffel/Spezialkommando
  membership(s); a Bereichsleitung/OL leader's **cascade-expanded** reach (their Bereich + its
  Staffeln/SKs, or — for the Organisationsleitung — every org unit), per
  [`org-unit-tenancy.md`](org-unit-tenancy.md) REQ-ORG-015; or, when an active org unit is pinned and
  within reach, exactly that one unit.
- Within scope, both **internal and public** missions of the viewer's own org units are eligible
  (REQ-MISSION-003's status filter still applies). Foreign missions are **excluded — including other
  units' public ones**; the cross-staffel public escape that widens the mission _lists_ deliberately
  does **not** apply to the banner, because the banner answers "what is _my_ unit heading towards".
- A viewer with **no** effective org-unit scope keeps the unchanged organisation-wide behaviour of
  REQ-MISSION-003: an admin in "all squadrons" mode (no active pin), an anonymous guest, and an
  authenticated user who belongs to no org unit all see the soonest eligible mission across the whole
  organisation (internal ones only for members).
- When no eligible mission exists in scope, the endpoint returns `204 No Content` and the page
  renders its empty state — unchanged.

**Acceptance**

- [ ] A member of Staffel A whose own next mission is later than Staffel B's public next mission sees Staffel A's mission, not Staffel B's.
- [ ] A Bereichsleitung sees the soonest mission across their Bereich's Staffeln/SKs; an OL member sees the soonest across every org unit.
- [ ] A member sees their own org unit's **internal** next mission (it is not hidden by the public-escape removal).
- [ ] An authenticated user with no org-unit membership, and an anonymous guest, still see the organisation-wide next mission (public-only for the guest).
- [ ] A scoped viewer with no upcoming own-unit mission gets `204`, even if other units have upcoming missions.

**Enforced by:** `MissionServiceTest`
(`getNextMission_scopedMember_usesScopedQueryThenRefetches`,
`getNextMission_scopedPinned_passesActiveOrgUnitId`,
`getNextMission_scopedMember_noUpcoming_returnsEmptyWithoutRefetch`,
`getNextMission_allowInternal_refetchesByIdThroughGraph` (admin all-scope fallback),
`getNextMission_guest_usesInternalFalseVariantThenRefetches` (membershipless fallback)),
`MissionRepositoryLookupOrderingTest`
(`findNextScopedMission_returnsOwnUnitNextSkippingForeignAndTerminal`,
`findNextScopedMission_allowInternalFalse_excludesOwnInternalMission`).
**Code:** `MissionService#getNextMission` + `#findNextScopedMissionHead`,
`MissionRepository#findNextScopedMission`,
`OwnerScopeService#currentScopePredicate` (scope vector + leadership cascade).

## Out of scope

- The guest field redaction applied to the returned mission DTO — owned by the security spec
  (see [`security-and-access.md`](security-and-access.md)).
- The broader mission list / search status filtering, which already accepts an explicit status set.
- The org-unit scope vector and the leadership cascade themselves — owned by
  [`org-unit-tenancy.md`](org-unit-tenancy.md) (`REQ-ORG-*`); this spec only consumes them.

## Open questions

None.
