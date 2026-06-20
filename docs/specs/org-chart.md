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

> **Widened by epic #692 (REQ-ORG-018):** the chart was originally a single "Profit-Bereich" tree
> (`OrgChartScope.AREA` as a singleton). With the real hierarchy (REQ-ORG-014: OL > Bereich > Staffel/SK)
> it becomes **multi-Bereich with an Organisationsleitung at the top**. It stays **purely descriptive**
> (REQ-ORG-010): a position grants no permission — authorization lives in the role model and
> `org_unit_membership` flags. The authoritative hierarchy and the descriptive chart are kept
> consistent by the admin who maintains both.

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
mouse. It is exposed as ARIA **tree**s — since epic #692 (REQ-ORG-018) the chart renders one
tree per tier: the Organisationsleitung on top, the Bereich tier-trees **side by side** beneath it
(joined to the OL by the same connector lines a Bereich draws to its Staffeln/SKs), then the
legacy/ungrouped tier — each its own `role="tree"` (labelled by its tier caption), each child row
`role="group"`, and
every box — person node, unit box, command head, vacant seat — a `role="treeitem"` carrying
an `aria-level` that matches its depth in that tier's reporting chain (within a Bereich /
legacy tier: 1 = Bereichsleiter / Area Lead, 2 = Stab member / Staffel- or SK-unit box,
3 = Staffel-/SK-Leiter, 4 = Kommandogruppe header / direct Ensign, 5 = Kommandoleiter,
6 = Stv. Kommandoleiter / Ensign within a Kommando; within the OL tree: 1 = OL root box,
2 = OL member) and an `aria-label` of "rank, name" (or "rank, nicht besetzt"). Parents are
`aria-expanded`; each Bereich additionally carries a collapse toggle that folds it to just its
Bereichsleiter (flipping the toggle's + the Bereichsleiter's `aria-expanded` and hiding the Bereich
body), and the keyboard nav skips the now-hidden treeitems.

Keyboard model: a roving tabindex keeps exactly one treeitem tabbable per tree; ↑/↓ move
between siblings, ←/→ between levels, Home/End to the ends. Keyboard focus is a sharp outline,
deliberately distinct from the Bereichsleiter's bloom. The inline editor's dialog traps
focus (Tab/Shift+Tab cycle within it), closes on Esc, returns focus to the control that
opened it, and renders the page chrome `inert` + `aria-hidden` while open. A successful edit
preserves the chart's horizontal scroll and the page's vertical scroll across the reload
(the editor reloads on success by design — see the concurrency notes in `CLAUDE.md`). The
"Bearbeiten" toggle exposes its state via `aria-pressed` and reveals a legend while editing.

**Acceptance**

- [ ] `GET /org-chart` renders `role="tree"`, `role="group"` and `role="treeitem"` with an
  `aria-level` per node, plus an `aria-pressed` edit toggle and the edit-mode hint.
- [ ] Exactly one treeitem per tier-tree is tabbable; the arrow keys + Home/End move focus
  within each tree.
- [ ] The dialog closes on Esc and returns focus to its trigger; the background is inert while
  it is open.
- [ ] A save keeps the scroll position (no jump to the top-left).
- [ ] Keyboard focus on a node is a visible outline, not only the hero bloom.
- [x] The Bereich tier-trees render side by side beneath the OL with OL→Bereich connector lines, and
  each Bereich carries a collapse toggle (`aria-expanded`) that folds it to its Bereichsleiter; a
  collapsed Bereich's hidden nodes drop out of the roving-tabindex order.

**Enforced by:** `OrgChartPageRenderTest` (asserts the rendered tree roles, the `aria-level`s,
the `aria-pressed` toggle and the edit-mode hint) and the Playwright e2e spec
`OrgChartKeyboardA11yE2eTest`, which drives the interactive, JavaScript-only behaviours: the
roving tabindex + arrow-key / Home/End navigation, the modal focus-trap / Esc / focus-return,
and the horizontal-scroll restoration across a successful edit (`@Tag("e2e")`, so it runs on the
ephemeral stack and is gated on the `e2e` PR label — see `.github/workflows/e2e.yml`).
**Code:** `org-chart.html` (inline tree-nav + per-Bereich collapse + dialog JS), `org-chart.css`
(`.oc-fan--bereiche`, `.oc-leader-wrap`, `.oc-collapse`, `.oc-bereich-body`),
`fragments/org-chart-node.html` (`ocBereich`) · **Issues:** —

### REQ-ORG-018 — Multi-Bereich chart with an Organisationsleitung level, coloured by Bereich

With the real hierarchy (REQ-ORG-014) the chart renders **OL → each Bereich (Bereichsleiter +
Bereichskoordinatoren + Bereichsoperatoren) → its Staffeln + SKs**, not a single Profit-Bereich.
`OrgChartScope.AREA` stops being a singleton: each Bereich has its own area-leadership sub-tree, and a
new top level holds the OL. The chart stays **descriptive and ADMIN-edited** (REQ-ORG-010) — it confers
no permission. Each Bereich's nodes are tinted with its frozen Bereichsfarbe
(`--color-dept-*`: Profit `#239E33`, Sub-Radar `#A3000A`, Raumüberlegenheit `#37BBC0`, Forschung
`#355DDC`, Marinekorps `#7A5E96`, Search & Rescue `#FFD23F`), keeping text contrast ≥ 4.5:1 and the ARIA
tree (REQ-ORG-013) intact.

**Acceptance**

- [x] `GET /org-chart` renders an OL root, then one area-leadership sub-tree per Bereich, then that
  Bereich's Staffeln + SKs; the partial unique indexes scope "one Bereichsleiter" etc. **per Bereich**.
- [x] Holding a chart position still grants no permission (a chart-only user is denied scoped data).
- [x] Each Bereich's nodes carry its Bereichsfarbe; contrast and the ARIA tree roles/levels are preserved.

The OL tier is carried by its own `OlChartDto` (id + name + members) so the inline editor can stamp a
new `OL_MEMBER` against the OL's org-unit id even while the tier is empty. The Bereichsfarbe is applied
as **border/swatch accents only** (the `oc-dept--<name>` class maps the `Department` enum name to a
`--color-dept-*` token); node text stays white/orange so every department hue clears the 4.5:1 text floor.
Each tier is its own ARIA tree (REQ-ORG-013). The Bereich tier-trees fan out **side by side** beneath the
OL — drawn with the same `.oc-fan` connector lines a Bereich uses for its Staffeln/SKs — and each is
individually **collapsible** to just its Bereichsleiter. The legacy/ungrouped Area-Lead tier is hidden
once it holds no content and a hierarchy exists, so an empty Area-Lead spine never lingers below a
populated chart.

**Enforced by:** `OrgChartPageRenderTest` (`olTier_admin_*`, `bereichTier_admin_*` — OL + per-Bereich
trees, the `oc-dept--profit` tint, the Bereichsleiter hero, the hidden legacy tier),
`OrgChartServiceTest#getOrgChart_groupsUnitsUnderBereichTierAndExposesOl` and the per-Bereich
`createPosition`/`validateCardinality` tests, migrations `V166` (`org_unit.department`) + `V167`
(widened `chk_org_chart_scope` + per-Bereich `BEREICHSLEITER` unique index) · **Code:**
`OrgChartService#getOrgChart`/`buildBereich`, `OlChartDto`, `BereichChartDto`, `org-chart.html`
(OL → Bereich connector fan + multi-tree keyboard nav + per-Bereich collapse), `fragments/org-chart-node.html`
(`ocBereich`, `ocUnitFan`), `org-chart.css` (`oc-dept--*`, `oc-bereich-tier`, `oc-fan--bereiche`,
`oc-collapse`) · **Issues:** #692, #698.

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
