> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-09.
> **Owner area:** ORG · **Related ADRs:** none

# Profit-Bereich org chart (Funktionsränge)

## Context & goal

The org chart (`/org-chart`, "Organigramm") is a purely **descriptive** view of who holds which
functional rank across the Bereichsleitung, the Staffeln and the Spezialkommandos. It grants no
permission — authorization stays with the role model and the `org_unit_membership` flags — so it is
deliberately not org-unit-scoped. An admin edits it inline; everyone else reads it. The aggregate is
the `OrgChartPosition` row (Flyway `V136`, extended by `V138`); the read/write rules live in
[`OrgChartService`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/OrgChartService.java)
and [`OrgChartController`](../../backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/OrgChartController.java).

## Requirements

### REQ-ORG-010 — The org chart is descriptive and ADMIN-edited

Placing a user in a position grants **no** permission; the chart only records functional ranks.
Reading the whole chart is open to every authenticated user; every mutation (create / reassign /
rename / vacate / remove) is gated to `ROLE_ADMIN` at the controller.

**Acceptance**

- [ ] `GET /api/v1/org-chart` succeeds for any authenticated caller and is forbidden to none of them.
- [ ] Every write endpoint under `/api/v1/org-chart/positions` requires `ROLE_ADMIN`.

**Enforced by:** `OrgChartControllerTest`, `SecurityTest` · **Code:** `OrgChartController` · **Issues:** —

### REQ-ORG-011 — A Kommando(gruppe) carries an independently fillable *and* vacatable Kommandoleiter

A `COMMAND_LEAD` row models the Kommando itself, not merely the person leading it: it carries an
optional group name and an optional holder (the Kommandoleiter). The seat may be **filled after** the
Kommando is created, and — crucially — **vacated without deleting the Kommando**. When the
Kommandoleiter post becomes vacant, the Kommandogruppe keeps existing: its name, its Stv.
Kommandoleiter and its Ensigns are untouched, and a new Kommandoleiter can later be assigned.
`COMMAND_LEAD` is the *only* rank allowed a `null` holder (the `chk_org_chart_user` CHECK in `V138`
keeps every other rank's holder mandatory); vacating is therefore rejected as a 400 for any other
rank — removing such a person-centric position is REQ-ORG-012 instead.

**Acceptance**

- [ ] Vacating a `COMMAND_LEAD` (`DELETE /positions/{id}/leader`) clears the holder and leaves the
  row, its name, its Stv. and its Ensigns in place.
- [ ] Vacating any non-`COMMAND_LEAD` rank is a 400 (`problem.org_chart.vacate_not_command`).
- [ ] A stale optimistic-lock version on vacate surfaces as a 409.
- [ ] A Kommando can be created leaderless and have a Kommandoleiter assigned later.

**Enforced by:** `OrgChartServiceTest` (`vacateCommandLeader_*`,
`createPosition_commandLeadWithoutUser_*`, `updatePosition_assignLeaderToLeaderlessKommando_persists`),
`OrgChartPageControllerTest` (`vacateLeader_*`) · **Code:** `OrgChartService#vacateCommandLeader`,
`OrgChartController#vacateCommandLeader` · **Issues:** —

### REQ-ORG-012 — Removing a Kommando cascades; vacating its leader does not

Removing a Kommando (`DELETE /positions/{id}` on a `COMMAND_LEAD`) deletes the row and, via the
`ON DELETE CASCADE` `parent_id` FK, its Stv. Kommandoleiter and the Ensigns reporting into it. This
is a distinct, more destructive operation than vacating the Kommandoleiter (REQ-ORG-011): the inline
editor warns the admin about the affected children before the delete, whereas vacating prompts only
to clear the seat.

**Acceptance**

- [ ] Deleting a `COMMAND_LEAD` removes the row; the DB cascade removes its Stv. and child Ensigns.
- [ ] The remove confirmation distinguishes "remove the whole Kommandogruppe" from "remove the
  Kommandoleiter".

**Enforced by:** `OrgChartServiceTest` (`deletePosition_present_deletes`), migration `V136`
(`parent_id … ON DELETE CASCADE`) · **Code:** `OrgChartService#deletePosition` · **Issues:** —

## Out of scope

- The per-Staffel / per-SK cardinality caps, scope/parent consistency and one-user-per-scope rules —
  they are pinned by the `createPosition_*` tests and documented in the `OrgChartService` Javadoc.
- Authorization semantics — the chart feeds none; see [`security-and-access.md`](security-and-access.md).

## Open questions

None.
