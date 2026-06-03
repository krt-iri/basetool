# Claude-Code-Auftrag — Keycloak `krt-theme` an das KRT-Design-System angleichen

Repo: `krt-iri/basetool`. Ziel: Das vorhandene Keycloak-Theme
`keycloak-theme/krt-theme/` (Login + Account) auf den **aktuellen** Stand des
KRT-Design-Systems bringen — Audiowide statt Ethnocentric, semantische Fehlerfarbe
(rot statt orange), einheitlicher Primär-Hover, CTA-Bloom, optional Wabenmuster,
sichtbarer Tastatur-Fokus. Das Theme ist bereits sehr gut; dies sind gezielte
Angleichungen, **keine Neugestaltung**.

## Quelle der Wahrheit
Das `das-kartell-design`-Skill (`.claude/skills/das-kartell-design/`):
- `keycloak/README.md` — verbindliche Keycloak-Design-Vorgaben.
- `keycloak/conformity-audit.md` — Befundliste (B1–B9) mit Prioritäten.
- `keycloak/krt-keycloak-tokens.css` — **Drop-in** `:root` + `@font-face` + `body`
  (ersetzt die entsprechenden Blöcke in beiden Theme-CSS-Dateien 1:1).
- `keycloak/keycloak-login-target.html` — Ziel-Optik (Soll).
- `colors_and_type.css` / `krt-components.css` / README → „Action hierarchy".
- Font: `fonts/Audiowide-Regular.woff2` (+ `.ttf`); Muster: `assets/honeycomb-bg.svg`.

Betroffene Dateien:
- `keycloak-theme/krt-theme/login/resources/css/krt-login-v3.css`
- `keycloak-theme/krt-theme/account/resources/css/krt-account-v3.css`
- `keycloak-theme/krt-theme/{login,account}/resources/fonts/`
- ggf. `keycloak-theme/krt-theme/{login,account}/resources/img/`

## Arbeitsweise
- Kleine, prüfbare Commits, ein Schritt pro Block. Theme nach Möglichkeit lokal
  (Keycloak-Container) gegen die echten FTL-Seiten sichten.
- PatternFly-Overrides **immer** für `pf-v5-`, `pf-v6-` und legacy `pf-c-` pflegen.
- Nur KRT-Tokens/-Werte verwenden; keine neuen Farben; eckig bleiben.
- Bestehende CSP/i18n (de/en) und die FTL-Templatestruktur nicht brechen.

---

## SCHRITT 1 🔴 — Audiowide + semantische Tokens + rote Fehler (eine `:root`-Operation)
1. **Fonts austauschen:** `Audiowide-Regular.woff2` + `.ttf` in
   `login/resources/fonts/` **und** `account/resources/fonts/` legen; die 6
   `Ethnocentric Rg.*`-Dateien (otf+woff2 je Theme) entfernen.
2. In **beiden** CSS-Dateien den `@font-face`-, `:root`- und `body`-Block durch
   `keycloak/krt-keycloak-tokens.css` ersetzen (Audiowide; `--font-headline:
   'Audiowide'`; semantische Tokens `--color-danger/-success/-warning/-info`;
   `--color-surface-input`; `--glow-primary/-danger`).
3. **Rote Fehler** (Befund B2): alle fehlerbezogenen Regeln von `--color-primary`
   auf `--color-danger` umstellen — u. a. `.krt-error-message`, sowie
   `#input-error`, `.kc-feedback-text`, `.pf-*-c-form__helper-text--error`,
   `.alert-error`/`.pf-*-c-alert.pf-m-danger`. Fehlerhafte Felder zusätzlich mit
   rotem Unterrand + `var(--glow-danger)`. Soll-Optik: roter Rand + `⚠`-Meldung
   (siehe Mock).
4. `.krt-monospace-text`: Orange → `--color-gray-1` (Backup-Codes bleiben lesbar;
   Orange ist für Aktion/Identität reserviert).

## SCHRITT 2 🟠 — Primär-Hover vereinheitlichen + CTA-Bloom
- Befund B3: **alle** Primär-Hover auf `--color-accent-light` (aufhellen) setzen —
  die `.krt-button`/`.pf-m-primary`-Regeln nutzen aktuell `--color-accent-dark`
  (verdunkeln), der `.btn`-Alias dagegen accent-light. In beiden Dateien angleichen.
- Befund B5: Primär-Button bekommt `box-shadow: var(--glow-primary)`. In Flows mit
  mehreren Aktionen (z. B. `login-config-totp`, OAuth-Consent) sicherstellen, dass
  genau **ein** gefüllter Primär-Button existiert; Sekundär/Cancel = Outline
  (`.krt-button-secondary` / `.pf-m-secondary` ist vorhanden).

## SCHRITT 3 🟡 — Honigwaben-Hintergrund (optional, reines Upgrade)
- `assets/honeycomb-bg.svg` als `honeycomb-bg.svg` in `login/resources/img/`
  **und** `account/resources/img/` legen.
- `body`-Hintergrund aus `krt-keycloak-tokens.css` übernehmen (flaches Schwarz +
  Waben ~46px + Top-Bloom). Den bestehenden Hard-Reset des PF-Grids/-Bilds
  **beibehalten** — nur der `<body>` bekommt das Muster.

## SCHRITT 4 🟡 — Tastatur-Fokus sichtbar (A11y)
- Befund B7: `:focus-visible { outline: 2px solid var(--color-primary);
  outline-offset: 2px }` für Buttons, Links, Checkbox/Radio und den Locale-Toggle
  ergänzen (Inputs haben bereits Orange-Glow). PF-eigene blaue Fokusringe weiter
  unterdrücken.

## SCHRITT 5 🟡 — Token-Sync dokumentieren
- Befund B9: Login- und Account-`:root` byte-identisch halten; im Header beider
  Dateien die Regel notieren „Token-Änderung in `colors_and_type.css` → hier im
  selben Commit nachziehen" (im Login bereits vorhanden, auf Account übertragen).

---

## Templates gegenprüfen (Optik, nicht neu bauen)
`login.ftl`, `login-username`/`-password`, `login-otp`, `login-reset-password`,
`login-update-password`, `login-verify-email`, `login-config-totp` (QR-Wrapper weiß
lassen!), `webauthn-*`, `login-oauth-grant`, `error.ftl`, `info.ftl`, `terms.ftl`
und die **Account-Konsole** (Nav, Form, Card, Header). Jede Seite erbt die Regeln;
stichprobenartig sichten, dass keine PF-Reste (Weiß/Blau/Grid, eckige Verstöße)
durchschlagen.

## NICHT ändern (bewusst gut)
- PF-Neutralisierung (CSS-Variablen + Selektor-Fallback + Pseudo-Reset, v5/v6/legacy).
- HUD-Eckklammern inkl. PF-Card-Fallback (`:not(:has(.login-container))`).
- WOFF2-first + `font-display: swap`, Frontend-Klassen-Aliasse, `#kc-header` aus,
  weißer QR-Wrapper.

## Definition of Done
1. Headlines rendern in **Audiowide**; keine Ethnocentric-Datei/-Referenz mehr.
2. Validierungsfehler erscheinen **rot** (`--color-danger`), Felder mit rotem Rand.
3. Primär-Hover hellt überall auf; Primär-Button trägt den Orange-Bloom; pro Screen
   genau eine gefüllte CTA.
4. (falls umgesetzt) Honigwaben-Hintergrund sichtbar, PF-Grid bleibt entfernt.
5. Tastatur-Fokus auf allen Controls sichtbar.
6. Login- und Account-Tokens identisch; Optik deckt sich mit
   `keycloak/keycloak-login-target.html`. Build/Theme lädt fehlerfrei (de + en).
