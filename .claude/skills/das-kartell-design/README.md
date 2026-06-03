# DAS KARTELL — Profit Basetool · Design System

A design system for the **Profit Basetool**, the squadron-management web app of the
**„DAS KARTELL" / IRIDIUM** organization in *Star Citizen*. It captures the brand's
dark, sci-fi "technical HUD" aesthetic — house orange on black, the angular
*Audiowide* display face, *Lato* body text, square-cornered containers framed
with corner brackets, and a department color system — so designers and agents can
produce on-brand interfaces, mockups and assets.

> **Aesthetic in one line:** dark-mode space-organization HUD — geometric, built
> from *rings (planets)* and *triangles (ships/stars)*, lit by a single signature
> orange `#E77E23`.

---

## Sources

This system was reverse-engineered from the real product and its official brand
manual. If you have access, explore these to go deeper:

- **GitHub — `krt-iri/basetool`** · <https://github.com/krt-iri/basetool>
  The full Spring Boot + Thymeleaf app. The visual source of truth is
  `frontend/src/main/resources/static/css/styles.css` (ported here into
  `colors_and_type.css` + `krt-components.css`), the templates under
  `frontend/.../templates/`, and the i18n copy in
  `frontend/.../messages_en.properties` / `messages_de.properties`.
  Brand assets live in `design/` (logos, fonts) and the custom Keycloak theme in
  `keycloak-theme/krt-theme/`.
- **Corporate Design Manual V2** (`docs/Styleguide.md` + the uploaded
  `KRT_Styleguide_V2.pdf`, dated 21.02.2016, *Restricted — internal use only*).
  This is the **authoritative** source for colors, type and the logo. Where the
  shipped code disagreed with the manual, **this design system follows the manual**
  (see *Color system → note on department names* below).

Reading the repo directly will always yield higher-fidelity recreations than
working from screenshots — start from `styles.css` and the templates.

---

## What the product is

The Profit Basetool is an internal tool for an *org* (player organization) in
*Star Citizen*. Members plan operations and earn/track in-game profit together.
Core areas:

- **Missions & Operations** — plan, brief, crew and review squadron missions; roll
  missions up into multi-mission operations with shared finances and payouts.
- **Hangar & Inventory** — track ships (manufacturer, insurance, fitted status) and
  personal/squadron material inventories.
- **Refinery & Materials** — manage refinery job orders, the price/material matrix
  across trade terminals, and profit calculations per ship/route.
- **Job Orders** — cross-squadron commodity requests with handover logging.
- **Members & Admin** — roles/permissions, UEX data sync, system settings.

The tenant unit is the **OrgUnit** — a **Staffel** (Squadron, e.g. IRIDIUM) or a
**Spezialkommando (SK)**. The default squadron is **IRIDIUM** (shorthand `IRI`).
German is the default language; English is fully translated.

---

## Color system

Full tokens in [`colors_and_type.css`](colors_and_type.css). Highlights:

| Role | Token | Hex |
| :-- | :-- | :-- |
| Hausfarbe (house orange) | `--color-primary` | `#E77E23` |
| Zierfarbe hell | `--color-accent-light` | `#EEB64B` |
| Zierfarbe dunkel | `--color-accent-dark` | `#C45C00` |
| Schwarz (page) | `--color-bg-black` | `#000000` |
| Grau 4 (surface) | `--color-gray-4` / `--color-bg-dark-gray` | `#141414` |
| Grau 3 (hairlines) | `--color-gray-3` | `#282828` |
| Grau 2 (muted) | `--color-gray-2` | `#646464` |
| Grau 1 (body text) | `--color-gray-1` | `#D2D2D2` |

**Bereichsfarben (departments)** — each Kartell department owns one fixed hue; the
values must never be altered, and never used for the logo:

| Department | Token | Hex |
| :-- | :-- | :-- |
| Raumüberlegenheit (Space Superiority) | `--color-dept-raumueberlegenheit` | `#37BBC0` |
| Forschung (Research) | `--color-dept-forschung` | `#355DDC` |
| Sub-Radar | `--color-dept-sub-radar` | `#A3000A` |
| Marinekorps | `--color-dept-marinekorps` | `#7A5E96` |
| Profit | `--color-dept-profit` | `#239E33` |
| Search and Rescue | `--color-dept-search-rescue` | `#FFD23F` |

**Semantic status** colors reuse those values by appearance: danger `#A3000A`,
success `#239E33`, warning `#FFD23F`, info `#355DDC`.

> **Note on department names.** The shipped `styles.css` mislabels three of the six
> Bereichsfarben (it calls `#A3000A` "combat", `#355DDC` "sub-radar", `#37BBC0`
> "research"). This system uses the **official manual** names above and keeps the
> old code names as deprecated aliases (`--color-dept-combat` → Raumüberlegenheit,
> etc.) so code written against the live app still resolves.

> **`#1C1C1C`** is a code-only half-step (input/table-head fill) exposed as
> `--color-surface-input`; it is **not** part of the official grayscale.

---

## Action hierarchy

How the orange accent is *allocated* — added 2026-06 after the `/missions/{id}`
page was reported as "too orange, the Anmelden button is hard to find". The root
cause was the accent doing double duty (identity **and** action) on a dense page,
so primary buttons stopped standing out.

**Rule: the filled orange accent marks the ONE primary action per context.**
Orange is for **action + identity** (logo, badges, headings) — never for plain
data values or every label.

The button ladder (`krt-components.css`), strongest → quietest:

| Class | Look | Use |
| :-- | :-- | :-- |
| `.btn.btn--cta` | filled orange + restrained bloom | the single primary action per panel — Anmelden, Speichern |
| `.btn-success` | filled green | status / state change — Check-In |
| `.btn-outline` | orange outline, not filled | emphasized secondary — Crew zuweisen |
| `.btn-ghost` | neutral hairline → orange on hover | routine repeated actions — Edit, Check-Out |
| `.btn-quiet-danger` | transparent → red on hover | destructive — Delete |

Supporting changes, all system-wide:

- **Form labels are neutral** (`--color-gray-1`), not orange — the input *values*
  should be the brightest thing in a form. Base `label` + `.form-label` updated.
- **Panel headers** use the calmer `.panel-header` component: surface fill + a
  single orange left-accent bar + light heading; orange kept for the chevron and
  active state only (instead of a full-orange bordered header).
- **Data values** (names, frequencies, IDs) use `.data-value` — bright white on a
  surface chip, because data is not an action.
- Named tokens: `--action-primary`, `--action-emphasis`, `--action-neutral`,
  `--data-fg`.

See the live before/after at
[`proposals/mission-detail-button-hierarchy.html`](proposals/mission-detail-button-hierarchy.html),
the specimen card `preview/components-button-hierarchy.html`, and the applied
pattern in `ui_kits/basetool/` (Missions → click a row → mission detail).

---

## Files

See the [**index**](#index) at the bottom for the full manifest.

- [`colors_and_type.css`](colors_and_type.css) — `@font-face` + all design tokens.
- [`krt-components.css`](krt-components.css) — component layer (buttons, hud-box,
  tables, forms, alerts, badges, toasts).
- [`preview/`](preview/) — Design System tab specimen cards.
- [`ui_kits/basetool/`](ui_kits/basetool/) — interactive recreation of the app.
- [`assets/`](assets/) — logos & favicon, plus the honeycomb `Wabenmuster`
  (`honeycomb.svg` bold + `honeycomb-bg.svg` faint ambient).
- [`fonts/`](fonts/) — Audiowide + Lato (self-hosted).

---

## Content fundamentals

How the product writes. Pulled from the real i18n strings.

- **Voice & tone.** Functional, military/technical, terse. The UI itself speaks
  plainly ("Add Ship", "Log Handover", "Mark as read"), but **system/error states
  lean into the sci-fi fiction**:
  - 403 → *"Access Denied — Insufficient security clearance. This incident has been logged."*
  - 404 → *"Signal Lost — The requested coordinates are invalid or the sector has been redacted."*
  - 500 → *"System Malfunction — Critical system failure detected. Technical personnel notified."*
  - Error CTA: *"Return to Base."*
- **Person.** Addresses the user as **you** ("This entry is only visible to you").
  Confirmations are matter-of-fact: *"Ship successfully added."*, *"Successfully saved."*
- **Casing.** Labels, table headers, nav, buttons and headings are **UPPERCASE**
  (headings via Audiowide; UI labels via CSS `text-transform`). Sentence case for
  body/help text. Status enums shout: `PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED`.
- **Buttons.** Imperative verbs: *Add, Edit, Save, Delete, Cancel, Check out,
  Log Handover, Create New Order.* Destructive actions always confirm
  ("Really delete ship?").
- **Bilingual & domain-loaded.** German is primary; many concepts keep German names
  even in English contexts: **Staffel** (squadron), **Spezialkommando / SK**,
  *Auftrag* (order), *Raffinerie* (refinery), *Lager* (inventory). Star Citizen
  jargon is assumed: SCU, UEX, LTI, terminals, quantum travel, refinery yield.
- **Numbers.** Money/quantities are integers with thousands separators; buy prices
  render red with a `−`, sell prices green with a `+`.
- **No emoji.** The brand uses none. Warnings use a `⚠` glyph; status uses small
  square dots. Iconography is a small in-house SVG line set (see *Iconography*).
- **Vibe.** You're an operator at a console on a capital ship — efficient, a little
  classified, never playful or cute.

---

## Visual foundations

Everything that makes a screen read as "Profit Basetool".

- **Mood / palette.** Near-black canvas (`#000`), `#141414` surfaces, text in
  `#D2D2D2`. One hero accent — house orange `#E77E23` — carries borders, headings,
  links, focus and primary actions. Color is used *sparingly and functionally*;
  department/semantic hues appear only as small tags, row tints and status.
- **Type.** Display = **Audiowide**, uppercase only, letter-spacing `0.05em`
  (a wide, rounded techno face). Body/UI = **Lato**, default weight **Light 300**, with
  **Bold 700** for emphasis/labels. Headings are orange; body is gray-1.
- **Backgrounds.** Flat black/dark-gray. No photographic hero imagery in-app. A
  subtle technical **pattern/texture** (`images/pattern.svg`) exists in the brand
  kit for marketing surfaces. The `.greeting` banner uses a single left-to-right
  dark→transparent gradient — gradients are otherwise avoided.
- **Containers / cards.** The signature is the **`.hud-box`**: a 1px `#282828`
  hairline border with **two diagonal corner brackets** (orange, top-left +
  bottom-right) drawn via `::before`/`::after`. Background is a translucent
  `rgba(20,20,20,0.5)`. Cards are **square** — no border-radius.
- **Corner radius.** Effectively **zero** everywhere — buttons, inputs, cards,
  modals, tables. The **only** rounded elements are **pill badges/chips**
  (`999px`) and the circular radio control.
- **Borders.** Hairlines `1px #282828`; accent borders `1px #E77E23`; headings and
  table heads sit on a **2px orange** bottom rule. Alerts use a **4px solid
  left border** in the status color over a 20%-tint fill.
- **Shadows / elevation.** No soft drop shadows for depth. The only "glow" is an
  **orange bloom** — `0 0 5px rgba(231,126,35,.3)` on input focus, `0 0 20px`
  rgba bloom on toasts/modals. Depth is signaled by hairlines + brackets, not by
  shadow. Drawers/dropdowns use plain black shadows for separation only.
- **Hover states.** Links/orange elements lighten to `#EEB64B`. Primary buttons
  lighten to `#EEB64B`. Table rows fill to `#282828`. Sidebar links shift right
  `padding-left: 10px` and turn orange. The hamburger bars get an orange glow.
- **Press / focus.** Focus = orange border + bloom (`:focus-visible` → `2px` orange
  outline, `2px` offset). No "shrink on press" animation.
- **Motion.** Restrained. `transition: …0.2s` on color/background for hovers; the
  sidebar drawer slides in over `0.4s cubic-bezier(0.4,0,0.2,1)`; the toast
  translates + fades over `0.5s`. No bounces, no parallax, no decorative motion.
- **Transparency & blur.** Overlays are `rgba(0,0,0,0.8)` + `backdrop-filter:
  blur(4px)` (sidebar overlay, modals). The squadron context chip uses a light
  `blur(2px)`. Used for focus/scrim, never decoration.
- **Tables.** Dense; `#141414` body, uppercase **light-gray** (`#D2D2D2`) headers on
  `#1C1C1C` with a **2px orange under-rule** (the orange is kept as the rule, not the
  text — consistent with the action-hierarchy work), ultra-subtle zebra
  (`rgba(255,255,255,0.02)`), full-row hover to `#282828`.
- **Scrollbars.** Square, 12px (`.scroll-thin` = 8px), on a `#141414` track with a
  leading hairline (`#282828`). Thumb is a flat `#646464` block in a 2px channel,
  lightening to `#EEB64B` on hover and `#E77E23` while dragging; the up/down
  buttons and the corner box are suppressed/filled so nothing renders as a bright
  OS default. Both axes. Wrap a scroll region in `.hud-scroll` + an axis class
  (`.scroll-y` / `.scroll-x` / `.scroll-both`); `.scroll-accent` makes the thumb
  orange at rest for emphasis panes (logs/consoles). Firefox via `scrollbar-color`.
- **Layout rules.** Sticky top **header** (`#141414`, 2px orange bottom border,
  becomes `accent-dark` in admin mode). Off-canvas **sidebar drawer** (380px) is
  the primary nav. **Fixed footer** pinned bottom with legal links + version.
  Persistent **squadron-context chip** fixed top-right. Content column capped at
  **1200px**; paragraphs at **80ch**. Touch targets ≥ **44px** everywhere.
- **Imagery color.** When imagery appears (manufacturer logos, ship art), it is
  monochrome/inverted to sit on black — the kit ships black + white SVG variants of
  each Star Citizen manufacturer logo.

---

## Interaction & responsive rules

From the project's own engineering guide (`CLAUDE.md` → *Frontend / UI rules*):

- **No native browser dialogs — ever.** Never use `confirm()`, `alert()` or
  `prompt()`. Destructive actions and messages use **KRT-styled modals and
  toasts** (the `.modal` + `.notification-toast` patterns in `krt-components.css`,
  reproduced in the UI kit). Confirmations read like *"Really delete ship?"* inside
  a bracketed modal; success/error feedback is a corner-bracket toast.
- **Department colors are semantic** — only use a Bereichsfarbe where that
  department/context actually applies (a combat unit tag, a research mission row),
  never as decoration.
- **Responsive is mandatory across four device classes:**
  | Class | Width | Rules |
  | :-- | :-- | :-- |
  | Smartphone | ≤ 768px | Touch-first; min 44px targets; single-column; wide tables scroll horizontally; context chip collapses to shorthand. |
  | Tablet | 768–1024px | Touch-first; 44px targets; collapse multi-column grids. |
  | Desktop | 1024–1600px | Auto-fit card/dashboard grids; off-canvas drawer nav. |
  | Ultra-wide | 1600px+ | Exploit space, but cap long-form text at `max-width: 80ch`; content column stays ≤ 1200px. |
- **i18n — every user-visible string is externalized.** German is the default
  locale, English is fully translated; there is **no hardcoded text** in templates,
  JS or Java (labels, buttons, tooltips, errors, placeholders, titles all come from
  `messages*.properties`). When authoring `.properties`, umlauts are `\uXXXX`-escaped;
  in Markdown they are literal UTF-8. Design with both languages in mind — German
  compounds run long, so don't pin label widths.



- **In-house SVG sprite, not a library.** Icons live as `<symbol>`s in a single
  hidden sprite (`fragments/icons.html` in the app) and are used via
  `<svg class="krt-icon"><use href="#krt-icon-NAME"/></svg>`. They inherit text
  color through `currentColor` and size to `1em` (`.krt-icon-lg` = 1.5em,
  `.krt-icon-xl` = 2em). **No CDN icon font** — the app's CSP forbids external
  CDNs, so the set is deliberately tiny and hand-curated.
- **Style.** 24×24 viewBox, **2px stroke**, round caps/joins, no fill — a clean
  line look that matches the technical HUD. The curated set: `close`, `chevron-
  down/up/left/right`, `warning`, `success`, `info`, `plus`, `minus`, `search`,
  `filter`, `edit`, `trash`. This set is reproduced in
  [`preview/brand-iconography.html`](preview/brand-iconography.html) and inside the
  UI kit.
- **Unicode as icons.** A few glyphs are used directly: `⚠` (warning), `▼`/`▶`
  (toggles), `×`/`&times;` (close), `✔` (checkbox tick), `▲` (number-spinners).
- **Emoji.** None — the brand does not use emoji.
- **Logos.** The brand mark is the **wedge-through-ring** (planet ring + four-point
  star + star-destroyer triangle). Available as `assets/krt.webp` (mark) and
  `assets/Kartelllogo.jpg` (full lockup). Logo may appear **only** in orange,
  white or black. *(The repo's `design/logos/*.svg` exports embed raster data that
  did not survive import; the `.webp`/`.jpg` rasters here are the usable copies.)*
- **Substitutions.** The display face was switched from the manual's *Ethnocentric*
  to **Audiowide** (self-hosted WOFF2). *Lato* and the icon set are originals. No
  Google Fonts or CDN fallbacks are required at runtime.

---

## Index

Root manifest:

| Path | What |
| :-- | :-- |
| `README.md` | This file. |
| `SKILL.md` | Agent-Skills entry point (for use in Claude Code). |
| `colors_and_type.css` | `@font-face` declarations + all color/type/shape tokens. |
| `krt-components.css` | Component CSS layer built on the tokens. |
| `fonts/` | Audiowide (display) + Lato (Thin→Black, each + italic) — self-hosted WOFF2 with TTF fallback. |
| `assets/` | `krt.webp` (mark), `krt-favicon.webp`, `Kartelllogo.jpg` (lockup), `honeycomb.svg` + `honeycomb-bg.svg` (Wabenmuster). |
| `preview/` | 23 Design System specimen cards (type, color, spacing, components, brand — incl. the rank ladder, honeycomb pattern, scrollbars and background/texture treatments). |
| `proposals/` | Design proposals + handoff: action-hierarchy before/after mocks (`mission-detail-…`, `list-page-…`, `inventory-…`, `refinery-order-…`), `scrollbar-mockups.html`, the bp-extractor audit, the full `template-audit.md`, and `claude-code-auftrag.md` (project-wide unification order for the real repo). |de-code-auftrag.md` (project-wide unification order for the real repo). |
| `slides/` | HUD slide template (deck-stage) — title, section, content, stats, comparison, quote, closing. |
| `ui_kits/basetool/` | Interactive recreation of the Profit Basetool app — see its own README. |

UI kits & decks:

- [`ui_kits/basetool/`](ui_kits/basetool/) — the squadron-management web app:
  header + sidebar drawer, dashboard, missions, hangar, materials price matrix,
  and the Keycloak-themed login.
- [`slides/`](slides/) — a 1280×720 HUD slide deck template built on `deck-stage.js`,
  using the honeycomb background, logo and HUD components.
