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

- **Headlines:** **Audiowide**, **UPPERCASE only**, letter-spacing `0.05em`. (Audiowide
  replaced Ethnocentric in 2026-06 — Ethnocentric is no longer used.) Never set body copy
  in the display face.
- **Body / UI:** **Lato**, default weight Light 300, Bold 700 for emphasis.
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

### REQ-UI-010 — Material-amount input: SCU quantity type

Every field where a user enters an amount of an **SCU-typed** material (inventory book-in,
book-out target + amount, material-order create + edit, material claim, handover, refinery
store-to-inventory) accepts a **positive decimal** value with **at most three decimal
places** — the SCU granularity is 0.001 (microSCU), so the input step is `0.001`. The
decimal separator may be typed as **either `.` or `,`** regardless of browser locale, and
the value is **normalised internally** to a canonical dot decimal before it leaves the
field (a native `<input type="number">` only accepts the browser-locale separator, which is
why these are `type="text" inputmode="decimal"`). A value typed with **more than three**
decimal places is **rounded to three using commercial rounding (round half up)** when the
field is committed (on blur) and again before submit. The value must be **> 0**; the one
exception is the book-out **target stock**, which may be `0` (= "remove all") and opts out
via `data-scu-allow-zero`.

**Acceptance**

- [ ] Both `0,01` and `0.01` are accepted and the form submits `0.01`.
- [ ] An amount with > 3 decimals is rounded half up to 3 (`0.0015` → `0.002`, `1.2345` → `1.235`, `12.9995` → `13`).
- [ ] An amount that is `0`, negative, or rounds to `0` (e.g. `0.0004`) is rejected before submit; the book-out target stock may be `0`.
- [ ] The field carries `step="0.001"` and is `type="text" inputmode="decimal" data-scu-decimal`.

**Enforced by:** `scu-decimal-input.js` (shared client helper; mode read from the live `step`
attribute) + `InventoryPageControllerMvcTest` (render-wiring) + web-asset linting (ESLint /
HTMLHint / Prettier). **Code:** `frontend/.../static/js/scu-decimal-input.js`,
`fragments/head.html` (`window.krtScuI18n`). **Issues:** PR #465.

### REQ-UI-011 — Material-amount input: PIECE quantity type

The same fields, when the chosen material is **PIECE-typed** (Stück), accept **only positive
whole numbers** (≥ 1). Decimal separators are **not** accepted — they are stripped as the
user types — and fractional or non-positive values are rejected before submit. The field's
integer mode is signalled by `step="1"`, which the quantity-type-aware page scripts set when
a PIECE material is selected.

**Acceptance**

- [ ] Only digits can be entered; a typed `.` or `,` is dropped.
- [ ] A value `< 1` (including `0`) is rejected before submit.

**Enforced by:** `scu-decimal-input.js` (integer mode) + web-asset linting. **Code:**
`frontend/.../static/js/scu-decimal-input.js`. **Issues:** PR #465.

## Out of scope

Brand assets/logos themselves (managed in the design skill `assets/`), and the desktop SC
Extractor's GUI design (see [`docs/DESIGN_SC_EXTRACTOR.md`](../DESIGN_SC_EXTRACTOR.md)).

## Open questions

- Should REQ-UI-008 (no native dialogs) and REQ-UI-005 (frozen hex values) get a dedicated
  ESLint/Stylelint rule so they are gate-enforced, not review-enforced? (Promote to an ADR
  if yes.)
- REQ-UI-010 / REQ-UI-011 are enforced at the input layer (client). Should the `> 0` and
  three-decimal-rounding / integer rules also be enforced **server-side** (DTO validation +
  rounding) so non-browser API clients cannot bypass them? (Currently the backend binds
  `Double` and the DB column scale is the only server-side guard.)

