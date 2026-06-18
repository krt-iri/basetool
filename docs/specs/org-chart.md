> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-09.
> **Owner area:** ORG · **Related ADRs:** none

# Profit-Bereich org chart (Funktionsränge)

## Context & goal

The org chart (`/org-chart`, "Organigramm") is a purely **descriptive** view of who holds which
functional rank across the Bereichsleitung, the Staffeln and the Spezialkommandos. It grants no
permission — authorization stays with the role model and the `org_unit_membership` flags — so it is
deliberately not org-unit-scoped. An admin edits it inline; everyone else reads it. The aggregate is
the `OrgChartPosition` row (Flyway `V136`, extended by `V138`); the read/write rules live in
[`OrgChartService`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/OrgChartService.java)
and [`OrgChartController`](../../backend/src/main/java/de/greluc/krt/profit/basetool/backend/controller/OrgChartController.java).

## Requirements

### REQ-ORG-010 — The org chart is descriptive and ADMIN-edited

Placing a user in a position grants **no** permission; the chart only records functional ranks.
Reading the whole chart is open to every authenticated user; every mutation (create / reassign /
rename / vacate / remove) is gated to `ROLE_ADMIN` at the controller.

**Acceptance**

- [ ] `GET /api/v1/org-chart` succeeds for any authenticated caller and is forbidden to none of them.
- [ ] Every write endpoint under `/api/v1/org-chart/positions` requires `ROLE_ADMIN`.

**Enforced by:** `OrgChartControllerTest`, `SecurityTest`, and the e2e spec
`OrgChartPositionCrudE2eTest` (drives create / rename / assign / reassign / vacate / remove through
the inline editor as an admin) · **Code:** `OrgChartController` · **Issues:** —

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
`OrgChartPageControllerTest` (`vacateLeader_*`), and the e2e spec
`OrgChartPositionCrudE2eTest#createsAssignsVacatesAndRemovesACommandLeader` (drives assign → vacate
through the UI and asserts the Kommandogruppe survives) · **Code:**
`OrgChartService#vacateCommandLeader`, `OrgChartController#vacateCommandLeader` · **Issues:** —

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
(`parent_id … ON DELETE CASCADE`), and the e2e spec `OrgChartPositionCrudE2eTest` (removes a Kommando
through the inline editor's confirm dialog) · **Code:** `OrgChartService#deletePosition` ·
**Issues:** —

### REQ-ORG-013 — The org chart is keyboard-operable and screen-reader-navigable

The chart conveys its hierarchy to assistive technology and is fully operable without a
mouse. It is exposed as an ARIA **tree**: the container is `role="tree"` (labelled by the
page title), each child row is `role="group"`, and every box — person node, unit box,
command head, vacant seat — is a `role="treeitem"` carrying an `aria-level` that matches its
depth in the reporting chain (1 = Bereichsleiter, 2 = Stab member / Staffel- or SK-unit box,
3 = Staffel-/SK-Leiter, 4 = Kommandogruppe header / direct Ensign, 5 = Kommandoleiter,
6 = Stv. Kommandoleiter / Ensign within a Kommando) and an `aria-label` of "rank, name" (or
"rank, nicht besetzt"). Parents are `aria-expanded` (the tree never collapses).

Keyboard model: a roving tabindex keeps exactly one treeitem tabbable; ↑/↓ move between
siblings, ←/→ between levels, Home/End to the ends. Keyboard focus is a sharp outline,
deliberately distinct from the Bereichsleiter's bloom. The inline editor's dialog traps
focus (Tab/Shift+Tab cycle within it), closes on Esc, returns focus to the control that
opened it, and renders the page chrome `inert` + `aria-hidden` while open. A successful edit
preserves the chart's horizontal scroll and the page's vertical scroll across the reload
(the editor reloads on success by design — see the concurrency notes in `CLAUDE.md`). The
"Bearbeiten" toggle exposes its state via `aria-pressed` and reveals a legend while editing.

**Acceptance**

- [ ] `GET /org-chart` renders `role="tree"`, `role="group"` and `role="treeitem"` with an
  `aria-level` per node, plus an `aria-pressed` edit toggle and the edit-mode hint.
- [ ] Exactly one treeitem is tabbable; the arrow keys + Home/End move focus through the tree.
- [ ] The dialog closes on Esc and returns focus to its trigger; the background is inert while
  it is open.
- [ ] A save keeps the scroll position (no jump to the top-left).
- [ ] Keyboard focus on a node is a visible outline, not only the hero bloom.

**Enforced by:** `OrgChartPageRenderTest` (asserts the rendered tree roles, the `aria-level`s,
the `aria-pressed` toggle and the edit-mode hint) and the Playwright e2e spec
`OrgChartKeyboardA11yE2eTest`, which drives the interactive, JavaScript-only behaviours: the
roving tabindex + arrow-key / Home/End navigation, the modal focus-trap / Esc / focus-return,
and the horizontal-scroll restoration across a successful edit (`@Tag("e2e")`, so it runs on the
ephemeral stack and is gated on the `e2e` PR label — see `.github/workflows/e2e.yml`).
**Code:** `org-chart.html` (inline tree-nav + dialog JS), `org-chart.css`,
`fragments/org-chart-node.html` · **Issues:** —

## Out of scope

- The per-Staffel / per-SK cardinality caps, scope/parent consistency and one-user-per-scope rules —
  they are pinned by the `createPosition_*` tests and documented in the `OrgChartService` Javadoc.
- Authorization semantics — the chart feeds none; see [`security-and-access.md`](security-and-access.md).
- Raising the connector-line contrast (the lines are `--color-gray-2`, ≈ 2.8:1 on black):
  evaluated and intentionally left as-is — the only lighter token (`gray-1`) on every line
  reads as noise, and the design system forbids inventing an intermediate grey. The lines
  stay calm and on-token.

## Open questions

None.
