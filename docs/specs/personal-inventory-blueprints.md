> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-11.
> **Owner area:** INV/UI · **Related ADRs:** none

# Personal inventory — "Meine Blueprints" master-detail (V3)

## Context & goal

The personal blueprint collection (`/personal-inventory/blueprints`) uses the **master-detail
layout (V3)** approved in the design system. Binding visual sources: the V3 block of
`.claude/skills/das-kartell-design/proposals/blueprints-page-varianten.html` and the canonised
components in `krt-components.css` (`.master-detail`, `.master-list`, `.master-row(.is-active)`,
`.detail-pane`, `.quality-block`/`.quality-row`/`.quality-affects`, `.empty-state`, `.tab-nav`,
`.krt-modal*`).

The rebuild is a pure presentation restructure: every endpoint, form, and behaviour of the
previous table layout is preserved.

## Requirements

### REQ-INV-008 — Master-detail structure, deeplink, and add bar

The page head keeps the greeting plus a facts subtitle ("N Blueprints · M mit Notiz") and carries
a compact **add bar**: the typeahead search (staged selections render as compact chips under the
bar; the "Hinzufügen" CTA stays disabled until something is staged) and the **JSON import**
button. The PI tabs (Items | Blueprints) render as a `.tab-nav` with a `.tab-count`.

The collection renders as `.master-detail`: left a filterable `.master-list` (one input doubles
as instant client-side row filter and — on submit — the existing `?q=` server filter), one
`.master-row` per blueprint (product name, ✎ marker when a note exists); right a permanent
`.detail-pane`. The active row syncs with a `?bp={id}` URL parameter (deeplink); ↑/↓/Home/End
move the selection (listbox semantics: `role="listbox"`/`option`, `aria-selected`). Empty
collection and no-selection states use `.empty-state`. On viewports ≤900px the layout collapses
to list → detail navigation with a back button in the pane.

### REQ-INV-009 — Detail pane on the existing lazy recipe endpoint, calculation unchanged

The detail pane shows product name, "Erhalten am" (UTC → local), note edit ✎ and remove 🗑 as
icon buttons (`title`+`aria-label`, reusing the existing modal flows), and **"Zutaten &
Stat-Beitrag nach Zutat-Qualität"** as one `.quality-block` per requirement group from the
existing lazy endpoint (`GET /personal-inventory/blueprints/{id}/recipe`, cached per id): source
slot, material + quantity (SCU/units) + min. quality, ONE quality slider spanning the group's
effective band (`aria-label` "Qualität {material}", `aria-valuetext`), and the group's affected
stats as chips with **live factors**. The factor interpolation is the pre-existing
implementation, kept verbatim (`computeModifierValue`: ordered segments — interpolated when
`linear`, held constant for stepped forms — else linear interpolation across the effective
band); the mock's `1 + (max−1)·q/1000` formula is explicitly NOT used. Slider state is
view-only (no persistence). Blueprints without requirement groups fall back to the flat
ingredient list with a dash. The note renders below the recipe only when present.

### REQ-INV-010 — No regression of functions

All previous functions stay: typeahead search + staging + batch add; JSON import (file pick,
preview modal with Alle/Keine/Anwenden, per-row search/notes); `?q=` server filter; note edit
modal (optimistic-locking `version`, hidden `acquiredAt`, maxlength 2000); remove confirm
(danger `.krt-modal--danger` with `.btn-danger`, naming the product); per-blueprint recipe data;
UTC→local "Erhalten am"; CSRF hidden inputs; CSP nonces. Modals use the `.krt-modal*` frame
(one filled CTA, ghost cancel, Esc).
