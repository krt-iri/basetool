> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-10.
> **Owner area:** MISSION · **Related ADRs:** none

# Home-page "next mission" banner

## Context & goal

The home page (`/`) shows a **next mission** banner: the single upcoming mission whose planned start
is the soonest still in the future. It is the squadron's at-a-glance "what's coming up". The banner
is guest-visible — anonymous visitors see only public missions; authenticated members also see
internal ones.

The banner must surface only missions that are still **operationally relevant**. A mission that has
already been `COMPLETED` or `CANCELLED` but happens to carry a future planned-start time (e.g. a
cancelled future plan, or a closed mission whose schedule was never corrected) is not something the
squadron is heading towards, and showing it as "next mission" is misleading.

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

## Out of scope

- The guest field redaction applied to the returned mission DTO — owned by the security spec
  (see [`security-and-access.md`](security-and-access.md)).
- The broader mission list / search status filtering, which already accepts an explicit status set.

## Open questions

None.
