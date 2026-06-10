> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
> **Owner area:** UI · **Related ADRs:** none yet · **Visual source of truth:** the design
> skill [`.claude/skills/das-kartell-design/README.md`](../../.claude/skills/das-kartell-design/README.md)
> (+ [`colors_and_type.css`](../../.claude/skills/das-kartell-design/colors_and_type.css)).

# UI & design system

## Context & goal

The Profit Basetool is the squadron-management web app of the "DAS KARTELL" / IRIDIUM
org. Its UI must read unmistakably as that brand: a dark sci-fi HUD, the orange house
colour, and the official corporate-design tokens — applied consistently across every
screen and all four device classes. The design skill is the **visual** source of truth;
this file is the **written, binding** contract that ships with the repo. Where the two
disagree, the skill wins and this file is corrected in the same PR.

> New UI/visual decisions are recorded in an ADR and reflected here and in the design
> skill — see the governance rules in `CLAUDE.md`.

## Requirements

### REQ-UI-001 — The DAS KARTELL design system is binding

Every UI change follows the design system. Do not invent colours, fonts, spacing, or
component shapes; reach for the published tokens in
[`colors_and_type.css`](../../.claude/skills/das-kartell-design/colors_and_type.css) and the
components in `krt-components.css`.

**Acceptance**

- [ ] No hard-coded colour/font/spacing values that duplicate an existing token.
- [ ] New components reuse the skill's component CSS rather than re-styling from scratch.

**Enforced by:** design review + web-asset linting (Stylelint / ESLint / HTMLHint).

### REQ-UI-002 — Brand colour & logo

The primary brand colour is **`#E77E23`** (orange). The logo appears **only** in this
orange, white, or black. Orange marks *action and identity* (CTAs, badges, headings),
never plain data values.

**Acceptance**

- [ ] Logo renders only in `#E77E23` / white / black.
- [ ] The single filled-orange CTA marks the one primary action per context.

### REQ-UI-003 — Dark-only surfaces

Backgrounds are **`#000000`** (page) and **`#141414`** (Grau 4 — header, footer, tables,
cards, sidebar). `color-scheme: dark`; there is **no light theme**.

### REQ-UI-004 — Typography

- **One typeface: Lato.** Body / UI default weight Light 300, Bold 700 for emphasis.
- **Headlines:** Lato too — distinguished by **weight (Bold 700)** + **UPPERCASE only** +
  letter-spacing `0.05em`, **not** by a separate display face. (History: Ethnocentric →
  Audiowide → consolidated to **Lato-only** in 2026-06; the Audiowide/Ethnocentric `@font-face`
  rules and font files were removed. `--font-headline` is kept as a Lato alias so existing
  `var(--font-headline)` references keep resolving.)
- The brand ships no monospace face; "mono" contexts use Lato with tabular figures.

### REQ-UI-005 — Department colours (Bereichsfarben) — values are frozen

The org's department colours are authoritative per Corporate Design Manual p.14:
*"Die Farbwerte dürfen weder abgewandelt noch verändert werden."* Use these names and
values exactly:

|              Department               |               Token               |    Hex    |
|---------------------------------------|-----------------------------------|-----------|
| Raumüberlegenheit (Space Superiority) | `--color-dept-raumueberlegenheit` | `#37BBC0` |
| Forschung (Research)                  | `--color-dept-forschung`          | `#355DDC` |
| Sub-Radar (covert)                    | `--color-dept-sub-radar`          | `#A3000A` |
| Marinekorps (Marine Corps)            | `--color-dept-marinekorps`        | `#7A5E96` |
| Profit                                | `--color-dept-profit`             | `#239E33` |
| Search and Rescue                     | `--color-dept-search-rescue`      | `#FFD23F` |

> **Deprecated aliases — do not use as names.** The shipped `styles.css` historically
> mis-named these (it called `#A3000A` "combat", `#355DDC` "sub-radar", `#37BBC0`
> "research"). Those survive only as deprecated CSS aliases (`--color-dept-combat`,
> `--color-dept-research`, `--color-dept-marine`) so old code resolves; always use the
> official names above. *(This corrects the inverted mapping that previously lived in
> `CLAUDE.md`.)*

**Acceptance**

- [ ] Department tags/badges use the official token names with the exact hex values.

### REQ-UI-006 — Semantic status colours

Status hues reuse Bereichsfarben values by appearance: danger `#A3000A`, success
`#239E33`, warning `#FFD23F`, info `#355DDC`. Treat them as status, not as the department.

**Accessible text tints.** The canonical danger/info/success hues are dark on the black
canvas (danger ≈ 2.3:1, info ≈ 3.6:1) and fail WCAG AA as small text. When a semantic
colour is the **text itself** (inline validation messages, status labels, price up/down),
use the lightened tints — `--color-danger-text` `#F2564B`, `--color-info-text` `#6C93EF`,
`--color-success-text` `#2EBC3D` (all ≥ 5:1 on black). Keep the canonical hues for fills,
borders and the brand Bereichsfarben tags. An invalid field additionally takes a red
hairline (`.input-error`) beside its `.field-error` message.

**Acceptance**

- [ ] Semantic colour used as small text uses the matching `*-text` tint, not the dark
  canonical hue; the canonical hues stay on fills/borders/tags.

### REQ-UI-007 — Visual style: square-first sci-fi HUD

Sci-fi / space-organisation / technical-HUD aesthetic: geometric shapes (rings,
triangles), thin technical markers framing content. **Corners are sharp** (`--radius-none`)
everywhere except pills (chips/badges) and circular controls. The orange bloom glow is the
only "shadow" idiom.

### REQ-UI-008 — No native browser dialogs

**Never** use `confirm()`, `alert()`, `prompt()`, or any native browser dialog. Build
KRT-styled modals/toasts instead.

**Acceptance**

- [ ] No `confirm(` / `alert(` / `prompt(` calls in frontend JS.

**Enforced by:** code/design review + ESLint (mechanical grep-able rule).

### REQ-UI-009 — Responsive across four device classes

Every layout change and new component works on **four** classes:

- **Smartphone** (≤768px) and **Tablet** (768–1024px) — touch first; minimum click target
  **44px**; collapse multi-column grids to one column; wide tables scroll horizontally.
- **Desktop** (1024–1600px) and **Ultra-wide** (1600px+) — exploit space (docked sidebars,
  auto-fit card/dashboard grids) but cap long-form text at `max-width: 80ch` on `<p>`.

**Acceptance**

- [ ] Verified at all four breakpoints; interactive targets ≥ 44px on touch classes.

### REQ-UI-010 — Standard action-button icons

The recurring CRUD actions use one fixed glyph from the in-house sprite (`fragments/icons.html`
in the app, `ui_kits/basetool/icons.jsx` in the design system): **delete / remove →
`krt-icon-trash`**, **edit → `krt-icon-edit`**, **save → `krt-icon-save`** (inventory book-out →
`krt-icon-bookout`). In **dense rows** (table / tree / compact action clusters) they render as
**icon-only** `.btn-icon` squares carrying their label in `title` + `aria-label`; in **forms and
dialogs** they render as **icon + text** (the glyph prepended before the label, which stays in a
`<span th:text>`). Decorative button glyphs set `pointer-events: none` (via `.btn .krt-icon`) so a
click always lands on the host `<button>` / `<a>`, never the inner `<svg>`. Danger styling
(`btn-quiet-danger` / `btn-outline-danger`) and existing `data-*` hooks are preserved. Mode toggles
whose label flips with state (e.g. the org-chart edit toggle) keep their text label.

**Acceptance**

- [ ] Delete / edit / save buttons use the matching sprite glyph; dense-row instances are icon-only
  with an accessible name in `aria-label` / `title`, form / dialog instances keep a visible label.
- [ ] Clicking the glyph triggers the button's action (no dead clicks on the inner `<svg>`).

**Enforced by:** code/design review · **Code:** `fragments/icons.html`, `static/css/styles.css`
(`.btn-icon`, `.btn .krt-icon`), the per-feature templates.

## Out of scope

Brand assets/logos themselves (managed in the design skill `assets/`), and the desktop SC
Extractor's GUI design (see [`docs/DESIGN_SC_EXTRACTOR.md`](../DESIGN_SC_EXTRACTOR.md)).

**Material-amount input fields** (SCU/PIECE precision, positivity, the `.`/`,` separator) are
cross-cutting (inventory, orders, refinery), so their rules live in their own spec —
[`inv-material-quantities.md`](inv-material-quantities.md) (REQ-INV-001 / REQ-INV-002) — not here.
This spec still governs how those fields *look*.

## Open questions

- Should REQ-UI-008 (no native dialogs) and REQ-UI-005 (frozen hex values) get a dedicated
  ESLint/Stylelint rule so they are gate-enforced, not review-enforced? (Promote to an ADR
  if yes.)

