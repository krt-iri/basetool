---
name: das-kartell-design
description: Use this skill to generate well-branded interfaces and assets for DAS KARTELL / IRIDIUM (the "Profit Basetool" Star Citizen squadron-management app), either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the `README.md` file within this skill, and explore the other available files.

This is a dark, sci-fi "technical HUD" brand for a Star Citizen org: house orange
`#E77E23` on black, the angular **Ethnocentric** display face (UPPERCASE only),
**Lato** body text (Light 300 / Bold 700), **square-cornered** containers framed with
diagonal corner brackets (the `.hud-box`), and a fixed department color system.

Quick start:
- **Foundations:** `colors_and_type.css` (`@font-face` + all color/type/shape tokens)
  and `krt-components.css` (buttons, hud-box, tables, forms, alerts, badges, toasts).
  Import both; build on the tokens — don't invent new colors.
- **Specimens:** `preview/` has cards for every token group.
- **Real components & screens:** `ui_kits/basetool/` is an interactive React recreation
  of the app (header, sidebar drawer, dashboard, missions, hangar, price matrix, login).
  Copy patterns from `components.jsx` / `screens.jsx` / `app.css`.
- **Assets:** `fonts/` (Ethnocentric + Lato, self-hosted) and `assets/` (logo mark,
  full lockup, favicon). The logo may appear ONLY in orange, white or black.

If creating visual artifacts (slides, mocks, throwaway prototypes), copy assets out and
create static HTML files for the user to view. If working on production code, copy assets
and read the rules here to become an expert in designing with this brand.

If the user invokes this skill without other guidance, ask them what they want to build
or design, ask some clarifying questions, and act as an expert designer who outputs HTML
artifacts _or_ production code, depending on the need.

Key brand rules (see `README.md` for the full content + visual foundations):
- Color is functional and sparing — one hero orange; department/semantic hues only as
  small tags, row tints and status.
- **Action hierarchy:** the filled orange CTA marks the ONE primary action per context
  (`.btn--cta`); demote routine actions to `.btn-ghost`/`.btn-outline` and destructive
  ones to `.btn-quiet-danger`. Orange is for action + identity, never for plain data
  values or every form label (labels are neutral gray; data uses `.data-value`).
- Corners are sharp everywhere; the ONLY rounded things are pill badges and the radio.
- Depth = hairlines + corner brackets + an orange bloom glow. No soft drop shadows.
- Headings & UI labels are UPPERCASE; system/error copy leans into the sci-fi fiction
  ("Signal Lost", "Return to Base"); no emoji.
- Department names follow the official Corporate Design Manual (Raumüberlegenheit,
  Forschung, Sub-Radar, Marinekorps, Profit, Search and Rescue) — not the shipped code's
  mislabels.
