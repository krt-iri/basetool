> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-27.
> **Owner area:** MISSION/UI · **Related ADRs:** [ADR-0044](../adr/0044-mission-ablauf-procedure-steps.md),
> [ADR-0057](../adr/0057-mission-goals-classified-ordered-children.md)

# Mission detail page — tab layout (Variante B)

## Context & goal

The mission detail page (`/missions/{id}`) uses the **tab layout (Variante B)** approved in the
DAS KARTELL design system. The binding visual sources are the mockups in the design-system
submodule (`.claude/skills/das-kartell-design/proposals/mission-page-tabs-gesamt.html`,
`mission-tab-crew-board.html`, `mission-tab-finanzen.html`, `mission-tab-uebersicht-verwaltung.html`,
`mission-modals.html`) and the canonised components in `krt-components.css` (`.tab-nav`,
`.facts-bar`, `.krt-modal*`, `.person-row`, `.drop-zone`, `.chip-select`).

The rebuild is a **pure presentation restructure**: every backend contract (endpoints, DTOs,
optimistic-locking versions) and every permission gate of the previous panel layout is preserved.

## Requirements

### REQ-MISSION-004 — Tab structure, deeplink, and state

The detail page renders a sticky head (title, owning-squadron badge, status pill, and a full-size
"Anmelden" CTA — #818 follow-up: the primary action is no longer a `btn-xs2`), a high-signal
`.facts-bar`, and a `.tab-nav` with up to four tabs. The facts bar (#818 follow-up) shows five
icon-led facts at larger type — TS meeting time (headset), server join = planned start (clock),
planned end (clock), party lead (user) and a combined participants fact (users) that folds the
checked-in count into the registered count (`registered · N eingecheckt`). The three time facts
show **time only** (`data-format="time"` on the `.krt-local-dt` span, localised client-side); the
full date stays in the Übersicht details. The standalone
finance-total fact was dropped (it stays on the Finanzen tab). Its `#facts-ts` /
`#facts-planned-start` / `#facts-planned-end` / `#facts-party-lead` / `#facts-registered` /
`#facts-checked-in` ids are patched in place by the overview / crew / party-lead live-update
handlers (carried on `#overview-head-meta` + `#crew-count-meta`) so a peer's schedule, party-lead
or check-in change never leaves the bar stale (REQ-FE-010). The four tabs:

1. **Übersicht** — read-only landing tab, re-split per the final Einsatz design (owner decision
   2026-06-27, superseding the 2026-06-11 consolidated single-`.kv-list` layout; the column sides were
   swapped and a "Ziele" box added by the REQ-MISSION-012 goals change). Two columns of stacked
   panels: **left** = a "Ziele" box (the structured, classified mission goals grouped Hauptziel →
   Nebenziel → Nicht-Ziel, REQ-MISSION-012), the read-only **Ablauf** checklist (REQ-MISSION-009), a
   "Teilnehmer" attendance meter (registered count + a checked-in progress bar derived from
   `checkedInParticipants/registeredParticipants`) and a "Kalender" open card; **right** = "Mission auf
   einen Blick" (planned/actual times, meeting time, `Treffpunkt`, operation, internal chip, party lead
   as a `.kv-list`, plus the caller's personal participation chip — the former single short objective
   `Ziel` row was removed, replaced by the Ziele box), "Weitere Leads" (the leadership-position rows)
   and "Funk" (the dynamic frequencies — the central, unit-less frequencies that carry a value,
   followed by the per-unit frequencies of the units that have one, each unit row tagged with a muted
   "Einheit" qualifier; central types without a value and units without a frequency are omitted and the
   whole panel collapses when nothing is set, #816). The long **Markdown**
   description moves into a collapsible gray-card `<details class="more">` below the grid — the same
   `--color-bg-dark-gray` panel surface as the other overview cards (owner request 2026-06-27, #818
   follow-up), with a chevron that flips on open, replacing the former bare `hud-details` summary that
   sat directly on the honeycomb backdrop (member+ gate unchanged; rendered
   server-side via the `@markdown` bean — raw HTML escaped, unsafe link protocols stripped, so
   `th:utext` never emits user-controlled markup; the same renderer feeds the home-page next-mission
   banner). The `#overview-actual-start` / `#overview-actual-end` / `#overview-party-lead` ids and the
   `freq-value-display` markers are preserved so the schedule / party-lead / frequency live-update
   patches keep working; because the Funk panel now also omits empty entries (#816), setting a
   central frequency additionally re-renders the overview section in place
   (`krtRefreshMissionSection('overview')`, not just a peer notify) so a first-ever value surfaces a
   fresh row, and every unit add/edit/delete refreshes `['crew','overview']` so the per-unit Funk
   mirror tracks the board (REQ-FE-010). The Wirtschaft jump card is dropped (the Finanzen tab is one click away). The
   page content is capped at 1800px — 1.5× the app's regular `--content-max` (1200px), because the
   board and finance grids carry side-by-side columns (owner decision 2026-06-11).
2. **Teilnehmer & Einheiten** — the crew board (REQ-MISSION-005).
3. **Finanzen & Auszahlung** — summary strip + finance ledger (member+ gate unchanged), payout
   table (public; participation % authenticated-only), and the Wirtschaft `<details>` sections
   (authenticated + data-present gates unchanged).
4. **Verwaltung** — role-gated (`canEdit` or `canManageManagers`); hidden otherwise. Contains the
   mission details form, organisation (party lead, frequencies), owner & manager administration,
   the **owning-org-unit reassignment** control ("Verantwortliche Einheit" — re-homes the mission to a
   different Staffel/SK/Bereich/OL or to ownerless; REQ-ORG-018), and the delete action (ADMIN only).
   The reassignment select offers the caller's assignable org units plus "Keine";
   saving it swaps the `#mission-mgmt-results` panel in place and repaints the sticky-head
   owning-squadron badge without a reload (REQ-FE-001).

The active tab synchronises with a `?tab=` URL parameter (`ueb|crew|fin|verw`); `?tab=` takes
precedence over `#tab=`, then a server-side validation-error hint (re-renders land on Verwaltung),
then the last tab from `localStorage`, then `ueb`. Browser back/forward re-applies the URL state.
Tabs use the WAI-ARIA tabs pattern (`role="tablist"/"tab"/"tabpanel"`, `aria-selected`, arrow-key
navigation). Switching away from a Verwaltung tab with unsaved form input asks for confirmation.
The create page (`/missions/new`) renders the details form without tabs.

### REQ-MISSION-005 — Crew board replaces the assign-crew modal (same backend)

Participants and units render as an assign board: a "Ohne Einheit" pool plus one open
`.drop-zone` per unit (units have **no fixed size**). Assignment works via drag & drop **and** a
click fallback (select person → click target) **and** keyboard (rows and zones are focusable;
Enter/Space selects and assigns) — all three drive the **existing** crew endpoints
(`POST/PUT/DELETE …/units/{u}/crew[/{c}]/ajax`); a drop equals the action of the former
"Crew zuweisen" modal. Moving between units is remove + add; dropping on the pool removes the
assignment. Releasing a drag **outside every `.drop-zone`** (over no unit and not over the pool)
also removes the unit assignment — the participant falls back into "Ohne Einheit", so a row deep in
a long board can be unassigned by dragging into empty space without scrolling to the pool; a pool
row dragged into empty space is a no-op. While a crew row is being dragged, holding the pointer
near the **top or bottom viewport edge auto-scrolls the page** (speed eases with depth into the
edge band) so units scrolled out of view stay reachable as drop targets; the scroll stops on drop /
drag-end.

Each person row shows: check-in status dot, name (+ guest chip), org-unit badges (incl. SK),
desired job, planned job, comment as a tooltip mark, on-board function(s), check-in/check-out
(only while the mission is running and the participant's time state matches), edit, and
unregister. Row actions keep their previous gates (`canEdit` or own row or guest row). The
on-board function is a `.chip-select` per person (canEdit only): a quick single-function setter
against the crew update endpoint, with an "Edit functions…" entry opening the multi-select crew
modal (multiple functions per person stay supported). Unit heads show name, ship type, responsible
(ship owner), HVU chip (`.chip--warning`), an "x an Bord" counter, and edit/delete (canEdit).

### REQ-MISSION-006 — KRT modals with danger confirms naming the consequence

All page modals (participant add/edit, unit add/edit, frequency, finance add/edit, crew functions,
delete confirm) use the `.krt-modal*` frame: one filled CTA per modal, ghost cancel, close-X with
`aria-label`, focus trap, Esc closes, backdrop click closes.

The **sign-up modal** carries an "Auszahlungsart" select: an explicit choice is stored on the new
participant and wins over the user's profile default; the empty "Standard" option keeps the
existing default chain (profile default for members per REQ-MISSION-002, `PAYOUT` for guests).

The **unit modal** matches the approved mock: ship type and hangar ship are offered
**separately** (hangar select filtered by type, with an explicit "— keines · nur Typ verwenden —"
option), the display name is **optional** (when blank the backend derives the stored name from the
assigned ship respectively ship type; without name, ship, and type the request is rejected), a
**Verantwortlich** select pins an explicit responsible person from the registered participants
(empty = automatic fallback to the assigned ship's owner, also in the board's unit head), an HVU
checkbox, the frequency field (existing function, kept beyond the mock), and a free-text **Notiz**
(shown as a tooltip mark in the unit head). `responsible_user_id` / `note` live on `mission_unit`
(V149); the responsible person is exposed as a PII-free `UserReferenceDto`.

The finance modal's type is an income/expense segment control mirroring into the classic `type`
form field. The delete confirmation uses the `--danger` variant and names the consequence per
sub-section (participant / unit incl. crew fallback to the pool / crew / mission / finance entry).

### REQ-MISSION-007 — No regression of permissions, contracts, or concurrency behaviour

Every `sec:authorize` / `th:if` permission gate of the previous layout carries over 1:1 (finance
panel member+; participation % authenticated; payout-select disable logic; participant actions
canEdit/own/guest; check-in/out time-state conditions; Wirtschaft authenticated + data; Verwaltung
by edit permission). Backend endpoints, DTOs and the optimistic-locking flow (`version` echo,
`data-version` DOM sync, 409 handling via `MissionSubresource`) are unchanged. Mission data shown
read-only to non-editors in the old Details panel remains visible via the Übersicht tab.

### REQ-MISSION-009 — Ablauf (procedure timeline) steps

A mission carries an ordered, reorderable list of **Ablauf** steps — a procedure timeline. Each step
is a persisted `MissionStep` child of the mission (`title` required ≤200 chars, optional free-text
`meta` "Zeit / Ort" hint ≤200 chars, a shared `done` flag, an explicit `orderIndex`). The Ablauf is
authored in the **Verwaltung** tab through a drag-sortable editor (`#mission-step-list`: per-row
title + meta inputs, up/down + drag reorder, delete, "Schritt hinzufügen", a live "N Schritte"
counter) and shown **read-only** in the Übersicht as an `<ol class="ablauf">` checklist whose single
**current phase** (`step--now`) is *derived* as the first not-done step (never stored). Edit-authorised
users (`mission.canEdit` / `@missionSecurityService.canManageMission`) toggle a step's shared `done`
check directly on the overview checklist; the state is visible to every viewer. Outsiders/guests see
the Ablauf read-only (it is non-PII planning data, forwarded like units/frequencies; ADR-0044).

All five mutations (add / edit / remove / reorder / done-toggle) go through dedicated slim endpoints
`…/missions/{id}/steps[/{stepId}][/reorder|/done]/slim` (`@PreAuthorize canManageMission`), each
echoing the mission's dedicated **`stepsVersion`** section counter — a manual `@OptimisticLock(excluded
= true) Long` in the `coreVersion`/`scheduleVersion`/`flagsVersion`/`partyLeadVersion` family — so an
Ablauf edit never 409s a concurrent core/schedule/flags edit, and a stale `stepsVersion` surfaces as
HTTP 409. Reorder reassigns `orderIndex` over the managed children by dirty-checking (no per-child
save, no clearing bulk query mid-loop) and records **one** event. Mutations re-render the editor +
overview-checklist fragments in place via `krtFetch`/`krtRefreshMissionSection(['steps','overview'])`
(no reload) and propagate to peers over the presence socket (REQ-FE-010, ADR-0031). Missionen is an
audited area: each mutation records a `MISSION_STEP_*` event (`ADDED` / `UPDATED` / `REMOVED` /
`REORDERED` / `DONE_CHANGED`) carrying only ids/counts/the done flag — **never** the step title or
meta (free text), per REQ-AUDIT-001. Migration: V192 (`mission_step` table + `mission.steps_version`).

### REQ-MISSION-010 — Rally point (Treffpunkt)

A mission carries the short free-text core-section field **`meetingPoint`** (Treffpunkt, ≤200 chars —
the rally point), edited in the Verwaltung details form and belonging to the **core** section (guarded
by `coreVersion`, persisted via the existing `/core` patch; no new lock). It is non-PII planning data,
forwarded to outsiders/guests like the units and frequencies (the long Markdown description remains the
one free-text field hidden from outsiders). Migration: V192 (`mission.meeting_point`).

> The former single short **`objective`** (Ziel, ≤250 chars, shown first in "Mission auf einen Blick")
> was **superseded by the structured, classified mission goals** of REQ-MISSION-012. V199 drops
> `mission.objective`, migrating each existing non-empty value into one Hauptziel so no planning data
> is lost.

### REQ-MISSION-011 — Operation detail page adopts the Variante B tab shell

The operation detail page (`/operations/{id}`) — the umbrella over missions, also an "Einsatz-Seite"
under #818 — is restructured from the legacy collapsible-column layout to the **same tab shell**
(sticky head + `.facts-bar` + `.tab-nav`) with five tabs: **Übersicht** (read-only landing: "Operation
auf einen Blick" status/mission-count/result/donations, an "Ergebnis je Einsatz" proportional result
bar per linked mission from the operation finance breakdown, an Einsätze preview list, and a
collapsible Markdown description), **Einsätze** (the paginated linked-missions table — REQ-FE-002 AJAX
pager unchanged — with an "Einsatz hinzufügen" shortcut that opens `/missions/new?operationId={id}`
with the operation preselected, editor-only), **Auszahlung** (the operation payout table + paid-out
toggle, unchanged), **Finanzen** (a summary strip + the per-mission finance breakdown as native
`<details>`), and **Verwaltung** (the details form — name / status / description — with the delete +
single Speichern CTA). This is a **frontend-only** restructure: operations have **no** owner or
per-operation managers (the mockup's owner/manager panels were clones of the mission design and are
deliberately omitted — edit access stays the role-based `canEdit`), and the read-only details form
remains visible (disabled) to non-editors as before.

The description field gains a **Markdown editor** (editor-only): a B / I / heading / list / link
formatting toolbar that wraps the textarea selection client-side, and a "Bearbeiten / Vorschau"
toggle whose preview is rendered **server-side** via `POST /operations/markdown-preview` through the
same `@markdown` (`MarkdownRenderer`) bean the page uses on save — so the preview is byte-identical to
the persisted render (raw HTML escaped, unsafe link protocols stripped). No backend, DTO, migration or
permission change; every existing operation contract (save / delete AJAX twins, payout paid-out
asymmetric authorization, missions pager) is preserved.

### REQ-MISSION-012 — Mission goals (Ziele) as classified, ordered children

A mission carries an ordered, reorderable list of **goals** (Ziele) that **replaces** the former
single short `objective` (REQ-MISSION-010). Each goal is a persisted `MissionObjective` child of the
mission (`title` required ≤250 chars, a `kind` classification, an explicit `orderIndex`). The
classification is one of three kinds — **Hauptziel** (`PRIMARY`), **Nebenziel** (`SECONDARY`) and
**Nicht-Ziel** (`NON_GOAL`, an explicit *non*-goal the operation deliberately does not pursue, stated
to bound the scope). A goal has **no** `done` flag (it is a scope statement, not a progress item like
an Ablauf step) and **no** free-text `meta`.

Goals are authored in the **Verwaltung** tab through a drag-sortable editor (`#mission-objective-list`:
per-row title input + a kind `<select>`, up/down + drag reorder, delete, "Ziel hinzufügen", a live "N
Ziele" counter) and shown **read-only** in the Übersicht as a dedicated **"Ziele" box** — the first
panel of the left column — that **groups** the goals by kind: all Hauptziele first, then Nebenziele,
then Nicht-Ziele, each group under its localized header, empty groups omitted, with an empty hint when
the mission has no goals. Edit access is the mission's `canManageMission` gate (no new permission).
Outsiders/guests see the Ziele box read-only — it is non-PII planning data, forwarded like the Ablauf
steps, units and frequencies (ADR-0057).

All four mutations (add / edit / remove / reorder) go through dedicated slim endpoints
`…/missions/{id}/objectives[/{objId}][/reorder]/slim` (`@PreAuthorize canManageMission`), each echoing
the mission's dedicated **`objectivesVersion`** section counter — a manual `@OptimisticLock(excluded =
true) Long` in the `coreVersion`/`scheduleVersion`/`flagsVersion`/`partyLeadVersion`/`stepsVersion`
family — so a goal edit never 409s a concurrent core / schedule / flags / Ablauf edit, and a stale
`objectivesVersion` surfaces as HTTP 409. Reorder reassigns `orderIndex` over the managed children by
dirty-checking (no per-child save, no clearing bulk query mid-loop) and records **one** event.
Mutations re-render the editor + overview-Ziele fragments in place via
`krtFetch`/`krtRefreshMissionSection(['objectives','overview'])` (no reload) and propagate to peers
over the presence socket (REQ-FE-010, ADR-0031). Missionen is an audited area: each mutation records a
`MISSION_OBJECTIVE_*` event (`ADDED` / `UPDATED` / `REMOVED` / `REORDERED`) carrying only ids / counts /
the **kind enum** — **never** the goal title (free text), per REQ-AUDIT-001. Migration: V199
(`mission_objective` table + `mission.objectives_version`), which also drops the legacy
`mission.objective` column after migrating each existing non-empty value into one `PRIMARY` goal.
Decision: [ADR-0057](../adr/0057-mission-goals-classified-ordered-children.md).
