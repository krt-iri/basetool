> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-11.
> **Owner area:** MISSION/UI · **Related ADRs:** none

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

The detail page renders a sticky head (title, owning-squadron badge, status pill, "Anmelden" CTA),
a `.facts-bar` (server join = planned start, TS meeting time, participants, checked-in count,
finance total for member+ viewers) and a `.tab-nav` with up to four tabs:

1. **Übersicht** — read-only landing tab: briefing/description (member+ gate unchanged), schedule /
   radio / leadership as a `.kv-list` (operation labelled singular; actual start and end as
   separate rows; the leadership-position rows precede the party lead), the caller's personal
   participation status, and a single Wirtschaft jump card (no Teilnehmer/Finanzen cards — those
   targets are one tab click away; owner decision 2026-06-11). The page content is capped at the
   app's regular `--content-max` width.
2. **Teilnehmer & Einheiten** — the crew board (REQ-MISSION-005).
3. **Finanzen & Auszahlung** — summary strip + finance ledger (member+ gate unchanged), payout
   table (public; participation % authenticated-only), and the Wirtschaft `<details>` sections
   (authenticated + data-present gates unchanged).
4. **Verwaltung** — role-gated (`canEdit` or `canManageManagers`); hidden otherwise. Contains the
   mission details form, organisation (party lead, frequencies), owner & manager administration,
   and the delete action (ADMIN only).

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
assignment.

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
