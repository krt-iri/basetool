# Profit Basetool — UI Kit

An interactive, high-fidelity recreation of the **DAS KARTELL / IRIDIUM Profit
Basetool** — the squadron-management web app. Built with React + Babel (in-browser)
on top of the shared design tokens. It is a *cosmetic* recreation for prototyping:
real layout, styling and click-through flow; fake data and no backend.

> Source of truth: `krt-iri/basetool` · `frontend/.../templates/` + `static/css/styles.css`.
> The live app is Thymeleaf server-rendered; this kit reproduces the look in React.

## Run it

Open `index.html`. It loads `../../colors_and_type.css`, `../../krt-components.css`
and `app.css`, then the JSX modules. Fonts and logos resolve from the project root
(`fonts/`, `assets/`).

## Flow

Login → dashboard → navigate via the hamburger **sidebar drawer**:

1. **Login** — Keycloak-styled access card on a faint HUD grid (any credentials work).
2. **Home / Dashboard** — `.greeting` banner, *Next Mission* + *Squadron Status*
   hud-boxes, a stale-data warning alert.
3. **Missions** — searchable table with department tags + status pills; "show past" filter.
4. **Hangar** — ship table with LTI badges, toggleable *fitted* dots, edit/delete row actions + toasts.
5. **Price Overview** — the commodity × terminal **price matrix** with collapsible categories.
6. **Mission detail** (Missions → click a row) — demonstrates the **action hierarchy**:
   one filled orange CTA per panel (Anmelden / Speichern / Hinzufügen), `.btn-outline`
   for Crew zuweisen, ghost Edit, quiet Delete, neutral labels, `.data-value` readouts,
   calm `.panel-header`s.
7. **Admin** (Members / UEX / Settings) — header turns to the `accent-dark` admin tint;
   Members is a real table, UEX/Settings are honest stubs (admin-only data not recreated).

## Files

| File | Role |
| :-- | :-- |
| `index.html` | App shell — auth + routing state, mounts everything. |
| `app.css` | Shell layout: header, sidebar drawer, footer, chip, login, dashboard, table extras. |
| `icons.jsx` | In-house SVG sprite + `<Icon>`. |
| `data.jsx` | Fictional, on-theme sample data. |
| `components.jsx` | Chrome: `Header`, `Sidebar`, `Footer`, `HudBox`, `Btn`, `Badge`, `StatusPill`, toasts. |
| `screens.jsx` | `LoginScreen`, `Dashboard`, `MissionsScreen`, `HangarScreen`, `MaterialsScreen`, `MembersScreen`. |
| `screen-mission-detail.jsx` | `MissionDetailScreen` — applied action hierarchy (CTA / outline / ghost / quiet). |

## Notes & fidelity

- Components are simplified cosmetic versions — not the app's real logic.
- Department **tags use the official manual names** (Raumüberlegenheit, Forschung,
  Sub-Radar, Marinekorps, Profit, Search & Rescue), not the shipped code's labels.
- Admin screens that depend on real data (UEX/Settings) are left as labelled stubs
  rather than invented — per "copy the design, don't reinvent it."
