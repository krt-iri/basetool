# Master-Auftrag — Profit Basetool app-weit + Keycloak-Theme am KRT-Design-System vereinheitlichen

Repo: `krt-iri/basetool`. Dieser Auftrag bündelt **beide** Baustellen zu einer
durchgehenden Reihenfolge:
- **A) Frontend/App** (Thymeleaf-Templates + `static/css/styles.css`)
- **B) Keycloak-Theme** (`keycloak-theme/krt-theme/` Login + Account)

Ziel: ein einheitlicher Marken-Look über App **und** SSO-Redirect hinweg, gemäß dem
aktuellen KRT-Design-System (Audiowide, eckig, HUD, Action-Hierarchie, neutrale
Labels/Datenwerte, semantische Statusfarben).

## Quelle der Wahrheit
Das `das-kartell-design`-Skill (`.claude/skills/das-kartell-design/`):
- `colors_and_type.css`, `krt-components.css`, README → „Action hierarchy".
- App-Details: `proposals/claude-code-auftrag.md` (+ Vorher/Nachher-Mocks
  `proposals/*-button-hierarchy.html`, `proposals/template-audit.md`).
- Keycloak-Details: `keycloak/claude-code-auftrag.md`, `keycloak/README.md`,
  `keycloak/conformity-audit.md`, `keycloak/krt-keycloak-tokens.css`,
  `keycloak/keycloak-login-target.html`.
- Referenz-Optik gesamt: `ui_kits/basetool/` (interaktiv).
- Assets: `fonts/Audiowide-Regular.woff2`(+`.ttf`), `assets/honeycomb-bg.svg`.

> Die beiden Detail-Aufträge sind verbindlich und enthalten die genauen
> Klassen-/Selektor-Mappings. Dieser Master-Auftrag gibt nur Reihenfolge,
> gemeinsame Regeln und die Definition of Done vor — bei Konflikten gelten die
> Detail-Aufträge.

## Gemeinsame Regeln (für A und B)
1. **Eine gefüllte Orange-CTA pro Kontext** (`.btn--cta` / Keycloak-Primärbutton).
   Sekundär = Ghost/Outline, destruktiv = Quiet-Danger, Status = Grün.
2. Orange = **Aktion + Identität** (Logo, Badges, Headlines) — **nie** für
   Formular-Labels, Tabellentext oder reine Datenwerte (`.data-value`).
3. **Audiowide** nur für Headlines, **Lato** für alles andere. Kein Ethnocentric.
4. Eckig (kein `border-radius`), HUD-Idiom, 44px-Targets, sichtbarer Fokus.
5. Status **semantisch**: Fehler rot (`--color-danger`), nie orange.
6. Keine neuen Farben — nur System-Tokens.

## Arbeitsweise
- Kleine, prüfbare Commits, ein Schritt pro Block. Nach jedem Schritt
  `./gradlew check`; App (`./gradlew run` o. ä.) und — wo möglich — das
  Keycloak-Theme lokal sichten.
- PatternFly-Overrides (Teil B) immer für `pf-v5-`, `pf-v6-` und legacy `pf-c-`.
- i18n (de/en), CSP-Nonce und Template-/FTL-Struktur nicht brechen.

---

## PHASE 0 — Fundament (zuerst, einmalig)
- Aktualisierte `colors_and_type.css` + `krt-components.css` aus dem Skill ins
  Frontend übernehmen bzw. `static/css/styles.css` an deren Tokens/Klassen
  angleichen. Neu: Button-Leiter (`.btn--cta/.btn-outline/.btn-ghost/
  .btn-quiet-danger`), `.panel-header`, `.data-value`, neutrale `label`-Defaults,
  hellgraue `th`, Scrollbar-System, Audiowide-`@font-face`, Action-Tokens.
- `Audiowide-Regular.woff2`(+`.ttf`) ins Frontend-`fonts/` legen; Ethnocentric
  entfernen.

## PHASE 1 — App: globaler Sweep (höchste Wirkung, kleinstes Risiko)
Nach `proposals/claude-code-auftrag.md` Schritt 2:
- Repo-weit Inline-Overrides entfernen, die System-Defaults aushebeln:
  `label { color: var(--color-primary) }`, `.form-group label`,
  `font-family:'Ethnocentric'`, Inline-Orange auf Datenwerten → `.data-value`.
  (Bekannt in `mission-detail`, `operation-detail`, `refinery-orders-details`,
  `admin/locations`, `material-collection`.)

## PHASE 2 — App: Listen-Seiten
Eine Regel für `missions`, `operations-index`, `orders-index`,
`refinery-orders-index` (Mock: `proposals/list-page-button-hierarchy.html`):
„Neu" → `.btn--cta`; Zeilen-„Details/Open" + „Filtern/Reset" → `.btn-ghost`;
„Delete" → `.btn-quiet-danger`; Modal-„Save" → `.btn--cta`, „Cancel" → ghost.

## PHASE 3 — App: Detail-Seiten
- `mission-detail` + `operation-detail` (Klon): `.col-header` → `.panel-header`-
  Optik, eine `.btn--cta` pro Panel, „Crew zuweisen" Outline, Edit/Check-Out Ghost,
  Check-In grün, Delete Quiet, Datenwerte `.data-value`.
- `orders-detail`, `refinery-orders-details` (eigenes Layout; Mock:
  `proposals/refinery-order-button-hierarchy.html`): eine CTA pro Abschnitt,
  Label-Override + inline-orange Titel entfernen, Datenwerte `.data-value`.

## PHASE 4 — App: Inventar & Hangar
Mock `proposals/inventory-button-hierarchy.html`: `inventory-index` (beide
Lager-Links Outline), `inventory-my`/`inventory-admin` (Erfassen → CTA, Bulk/Zeilen
→ Ghost/Outline, destruktiv → Quiet, je Modal 1 CTA), `hangar`, `material-collection`.

## PHASE 5 — App: Konsolidierung
Toast vereinheitlichen (`.notification-toast`), kanonische Tabellen-Klasse,
`var(--color-text)` → `--color-gray-1`, redundante Inline-Styles entfernen.

## PHASE 6 — Keycloak-Theme
Vollständig nach `keycloak/claude-code-auftrag.md`:
1. Audiowide + semantische Tokens + **rote Fehler** (Drop-in
   `keycloak/krt-keycloak-tokens.css` ersetzt `:root`/`@font-face`/`body` in
   Login **und** Account; Ethnocentric-Dateien raus).
2. Primär-Hover vereinheitlichen (`--color-accent-light`) + CTA-Bloom.
3. (optional) Honigwaben-Hintergrund; PF-Grid-Reset behalten.
4. Tastatur-Fokus sichtbar (`:focus-visible`).
5. Login- und Account-Tokens byte-identisch + Sync-Kommentar.
- FTL-Langliste gegensichten (login/otp/reset/verify/totp(QR weiß)/consent/error/
  info/terms + Account-Konsole). Keine PF-Reste (Weiß/Blau/Grid).

---

## Definition of Done (gesamt)
**App**
1. Pro Seite genau eine primäre Aktion auf einen Blick (eine gefüllte CTA).
2. Kein Label / reiner Datenwert orange; Tabellenköpfe hellgrau (Orange-Unterrule bleibt).
3. Wiederholte Zeilenaktionen ruhig (ghost/quiet) — keine Orange-Kaskade.
4. Keine Inline-Overrides hebeln System-Tokens/-Komponenten mehr aus.
5. Audiowide nur in Headlines, sonst Lato.

**Keycloak**
6. Headlines Audiowide; keine Ethnocentric-Referenz/-Datei mehr.
7. Validierungsfehler rot (`--color-danger`), Fehlerfelder mit rotem Rand.
8. Primär-Hover hellt auf; Primär-Button mit Bloom; eine CTA pro Screen;
   Login-/Account-Tokens identisch; Optik = `keycloak/keycloak-login-target.html`.

**Gesamt**
9. `./gradlew check` grün; App + Keycloak laden fehlerfrei (de + en); Optik deckt
   sich mit `ui_kits/basetool/` und den Mocks in `proposals/` + `keycloak/`.

> Reihenfolge nach Wirkung/Risiko: **0 → 1 → 2 → 3 → 4 → 5 → 6**. Phase 1 (Sweep)
> und Phase 6.1 (Keycloak-Tokens+rote Fehler) bringen den größten Gewinn pro
> Risiko und sollten früh gemerged werden.
