# Keycloak pages — KRT design guidance

How the Keycloak login, account-console and all auth flows (OTP, reset, verify,
consent, errors) must look so the SSO redirect feels like the same product as the
Profit Basetool. Keycloak ships PatternFly; our job is to **override** it into the
KRT HUD aesthetic, not to restyle from scratch.

Source of truth: this skill's `colors_and_type.css` / `krt-components.css`. The
Keycloak themes can't `@import` the app CSS (isolated resource path), so they
**mirror** the tokens — see `krt-keycloak-tokens.css` (drop-in `:root` + fonts +
body). Target look: `keycloak-login-target.html` (open it).

In-repo theme: `keycloak-theme/krt-theme/` (`login/` + `account/`), parent
`keycloak`, default locale `de` (also `en`).

---

## Principles

1. **One identity across the redirect.** Same orange `#E77E23`, black canvas,
   Audiowide headlines, Lato body, square corners, HUD corner brackets. A user
   bounced to Keycloak for SSO should not notice a visual seam.
2. **Override PatternFly, don't fight it twice.** Neutralise PF with its own CSS
   variables (`--pf-*--BackgroundColor`, `--pf-global--primary-color--100`, …) AND
   a selector fallback. Always cover v5 **and** v6 prefixes (`pf-v5-`, `pf-v6-`,
   legacy `pf-c-`) — Keycloak upgrades PF between releases.
3. **Kill the default chrome.** Remove PF's blue focus rings, white cards, the
   background grid/image, pseudo-element borders (`::before`/`::after`) and bright
   menu-toggle fills. Replace with KRT surfaces + hairlines + brackets.
4. **Action hierarchy applies here too.** Exactly one filled orange primary per
   screen (Sign in / Submit / Save). Secondary/Cancel = orange **outline**.
   Links = orange text. Don't fill two buttons.
5. **Semantics, not brand color, for state.** Validation errors are
   `--color-danger` (red), success `--color-success`. **Never** show errors in the
   brand orange (today's theme does — see audit).
6. **Accessibility.** Keep a visible keyboard focus state (orange border/glow);
   never strip focus to nothing. Maintain 44px targets. Labels stay readable
   light-gray, uppercase.

---

## Component specs

**Card / form container**
- `background: #141414`; `border: 1px solid #282828`; `border-top: 3px solid #E77E23`.
- HUD corner brackets top-left + bottom-right (2px orange, 15px), via
  `::before`/`::after`. `box-shadow: 0 0 20px rgba(231,126,35,0.10)`.
- `max-width: 450px`, `padding: 40px`, centered; square (no radius).

**Headline** — `#kc-page-title` / `h1`: Audiowide, uppercase, `+0.05em`, orange,
centered. Optional brand mark (`krt.webp`, ~84px) above it.

**Inputs** — black fill, `1px #646464` border, `2px #E77E23` bottom border, square,
Lato, `#D2D2D2` text. Focus: border → orange + `0 0 8px rgba(231,126,35,0.25)`.
Placeholder: italic `#646464`.

**Buttons**
- Primary (`.pf-m-primary`, `#kc-login`, submit): filled orange, **black** text,
  Audiowide uppercase, square, `min-height: 44px`, `box-shadow: var(--glow-primary)`.
  Hover → `--color-accent-light` (lighten). *(Not accent-dark.)*
- Secondary / Cancel (`.pf-m-secondary`, `#kc-cancel`, `.krt-button-secondary`):
  transparent, orange text, `1px` orange border. Hover → orange fill, black text.

**Checkbox / radio** — square (radio round), black fill, orange border, orange tick
on check (black checkmark). 18px.

**Labels** — `#D2D2D2`, uppercase, `0.85em`, `+1px` tracking. Not orange.

**Errors** — field: red bottom border + `var(--glow-danger)`. Message
(`#input-error`, `.kc-feedback-text`, alert-error): `--color-danger`, with a `⚠`
glyph. Success/info alerts use `--color-success` / `--color-info` left-border tint.

**Links** (`#kc-info a`, reset/register): orange, hover → accent-light / underline.

**Locale selector** — top-right; surface fill + hairline + orange bottom-border;
dropdown is a dark hairline menu, items hover to orange fill / black text.

**Page background** — flat black + faint honeycomb (`honeycomb-bg.svg`, ~46px) +
soft top bloom. Still hard-reset the PF background grid/image everywhere else.

---

## Templates to cover (don't forget the long tail)
`login.ftl`, `login-username/password`, `login-otp`, `login-reset-password`,
`login-update-password`, `login-verify-email`, `login-config-totp` (QR — keep the
`.krt-qr-wrapper` white box), `webauthn-*`, `login-oauth-grant` (consent),
`error.ftl`, `info.ftl`, `terms.ftl`, and the **account console** (nav, forms,
cards, page header). Every one inherits the rules above.

---

## Do / Don't
- ✅ Mirror tokens from `krt-keycloak-tokens.css`; cover `pf-v5-`+`pf-v6-`.
- ✅ One primary button per screen; outline for secondary.
- ✅ Errors in red, focus always visible.
- ❌ No Ethnocentric (replaced by Audiowide), no rounded corners, no PF blue, no
  white cards, no orange error text, no background grid.
