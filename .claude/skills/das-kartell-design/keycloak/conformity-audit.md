# Konformitätsaudit — Keycloak `krt-theme` gegen das KRT-Design-System

Geprüft im Repo `krt-iri/basetool`: `keycloak-theme/krt-theme/` — Login- und
Account-Theme (`login/resources/css/krt-login-v3.css` 28 KB,
`account/resources/css/krt-account-v3.css` 10 KB, beide `theme.properties`,
mitgelieferte Fonts + `login/.../img/krt.webp`).
Referenz: das `das-kartell-design`-Skill. Ziel-Optik: `keycloak-login-target.html`.
Drop-in-Tokens: `krt-keycloak-tokens.css`. Vorgaben: `README.md` (dieser Ordner).

## Gesamturteil

**Sehr gute, durchdachte Umsetzung.** Das Theme neutralisiert PatternFly sauber
(v5/v6/legacy, CSS-Variablen + Selektor-Fallback + Pseudo-Element-Reset), nutzt die
KRT-Farben exakt, ist eckig, hat HUD-Eckklammern, WOFF2-Fonts mit `font-display:
swap` und sogar Frontend-Klassen-Aliasse (`.btn`, `.btn-secondary`, `.btn-danger`).
Die Befunde sind **Angleichungen an zwei neuere System-Entscheidungen** (Audiowide,
Semantik/Action-Hierarchie) plus ein paar UX-Feinheiten — keine groben Fehler.

---

## Konform — ✅

| Bereich | Status | Beleg |
| :-- | :-- | :-- |
| Hausfarben | ✅ exakt | `:root` in beiden Dateien: `#E77E23`, Grau 1–4, Akzente |
| Eckig | ✅ | `border-radius: 0 !important` auf Inputs/Buttons/Checkboxen/Cards |
| HUD-Eckklammern | ✅ | `.login-container::before/::after` (+ PF-Card-Fallback `:not(:has(.login-container))`) |
| Dunkler Grund | ✅ | Hard-Reset entfernt PF-Grid/-Weiß/-Bild (v5/v6/legacy) |
| Inputs | ✅ | schwarz, `1px gray-2` + `2px` Orange-Unterkante, Fokus-Glow orange |
| Primär-Button | ✅ Form | orange Fläche, schwarzer Text, Audiowide*, uppercase, eckig |
| Sekundär-Button | ✅ | transparent + Orange-Border, Hover → Orange-Fill |
| Checkbox | ✅ | eckig, schwarz, Orange-Haken (schwarzer Tick) |
| Labels | ✅ | `--color-gray-1` uppercase — **bereits neutral** (entspricht der neuen Regel!) |
| Fonts | ✅ Technik | WOFF2 zuerst, TTF/OTF-Fallback, `font-display: swap` |
| PF-Override | ✅ | v5 **und** v6 **und** legacy `pf-c-` abgedeckt |
| Locale-Dropdown | ✅ | dunkel, Hairline, Hover → Orange-Fill |
| Klassen-Aliasse | ✅ | `.btn/.btn-secondary/.btn-danger` spiegeln Frontend |

\* siehe Befund 1 — aktuell noch Ethnocentric statt Audiowide.

---

## Abweichungen / Verbesserungen

Priorität: 🔴 hoch · 🟠 mittel · 🟡 niedrig.

### 🔴 1 — Display-Font noch Ethnocentric
Beide CSS-Dateien deklarieren `@font-face 'Ethnocentric'` und setzen
`--font-headline: 'Ethnocentric'`; im Ordner liegen 6 Ethnocentric-Dateien. Das
System wurde auf **Audiowide** umgestellt.
→ **Fix:** `Audiowide-Regular.woff2/.ttf` in beide `resources/fonts/` legen,
`@font-face` + `--font-headline` auf Audiowide ändern, Ethnocentric-Dateien
entfernen. Drop-in: `krt-keycloak-tokens.css`. (Audiowide hat nur einen Schnitt,
kein Italic — genügt für Headlines.)

### 🔴 2 — Fehler werden orange statt rot dargestellt
`.krt-error-message { color: var(--color-primary) }` und `.krt-monospace-text`
(orange) zeigen Validierungs-/Fehlermeldungen in der **Markenfarbe**. Das ist ein
Anti-Pattern: Fehler müssen als Gefahr lesbar sein.
→ **Fix:** `--color-danger: #A3000A` in `:root` ergänzen (fehlt aktuell!) und
Fehlermeldungen + Fehler-Feldränder darauf umstellen (`#input-error`,
`.kc-feedback-text`, `.pf-*-c-form__helper-text--error`, `.alert-error`).
Ziel-Optik im Mock: roter Unterrand + `⚠`-Meldung.

### 🟠 3 — Primär-Hover verdunkelt (inkonsistent)
Die Hauptregel setzt `.krt-button:hover → --color-accent-dark` (#C45C00, dunkler),
**aber** der `.btn`-Alias unten nutzt korrekt `--color-accent-light` (#EEB64B,
heller). Im selben File zwei verschiedene Hover-Logiken; System-Standard ist
**aufhellen**.
→ **Fix:** alle Primär-Hover auf `--color-accent-light` vereinheitlichen
(Login + Account).

### 🟠 4 — `--color-danger` & semantische Tokens fehlen in `:root`
`.btn-danger` greift auf `var(--color-dept-combat, #A3000A)` zurück — die Variable
ist aber nirgends definiert, es zieht der Hardcode-Fallback. `--color-success/
-warning/-info` fehlen ebenfalls.
→ **Fix:** semantischen Block aus `krt-keycloak-tokens.css` übernehmen.

### 🟠 5 — Action-Hierarchie / CTA-Bloom
Primär-Button hat keinen `--glow-primary`-Bloom; auf Mehr-Button-Flows (z. B.
`login-config-totp`, Consent) ist nicht garantiert „eine gefüllte CTA".
→ **Fix:** `box-shadow: var(--glow-primary)` auf den Primär-Button; in Flows mit
mehreren Aktionen Sekundär konsequent als Outline (ist als Klasse vorhanden).

### 🟡 6 — Kein Honigwaben-Hintergrund
Das Theme setzt den Grund bewusst auf flaches Schwarz (entfernt das alte PF-Grid).
Das System hat inzwischen das Wabenmuster; die App-Login nutzt es.
→ **Optional:** `honeycomb-bg.svg` in `login/.../img/` + `account/.../img/` legen
und als dezenten `body`-Hintergrund (~46px) + Top-Bloom einsetzen (siehe
`krt-keycloak-tokens.css`). Flaches Schwarz bleibt markenkonform — reines Upgrade.

### 🟡 7 — Tastatur-Fokus nur auf Inputs klar
Inputs haben einen Orange-Fokus-Glow ✅; Buttons/Links/Checkbox verlassen sich auf
`:hover`/`:active`. Für Tastaturnutzer fehlt ein klarer `:focus-visible`-Zustand.
→ **Fix:** `:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px }`
auf Buttons/Links/Checkbox.

### 🟡 8 — „mono" via `monospace`
`.krt-monospace-text { font-family: monospace }`. Das System bildet „mono" auf Lato
ab (tabular-nums). Für Backup-Codes ist echtes Monospace vertretbar; falls strikte
Konformität gewünscht: auf Lato + `tabular-nums` umstellen. Niedrig.

### 🟡 9 — Token-Duplikate doppelt pflegen
Login- und Account-`:root` sind getrennte Kopien. Unvermeidlich (Keycloak kann die
App-`styles.css` nicht laden), aber fehleranfällig.
→ **Empfehlung:** beide identisch aus `krt-keycloak-tokens.css` speisen und im
Header-Kommentar die „in einem Commit mitziehen"-Regel notieren (ist im Login bereits
dokumentiert — auf Account übertragen).

---

## Nicht ändern (bewusst gut) ✅
- PF-Neutralisierung über CSS-Variablen + Selektor-Fallback + Pseudo-Reset (v5/v6/legacy).
- HUD-Eckklammern inkl. Fallback für native PF-Cards (`:not(:has(.login-container))`).
- WOFF2-first-Fonts, `font-display: swap`, Frontend-Klassen-Aliasse.
- `#kc-header { display:none }` + eigener Header in der Form.
- QR-Wrapper mit weißem Hintergrund (`.krt-qr-wrapper`) — für Scanbarkeit korrekt.

---

## Priorisierte Reihenfolge
1. 🔴 **1 (Audiowide)** + 🔴 **2/🟠 4 (Semantik-Tokens + rote Fehler)** — gemeinsam,
   gleiche `:root`-Stelle, größte Marken-/UX-Wirkung.
2. 🟠 **3 (Hover vereinheitlichen)** + 🟠 **5 (CTA-Bloom)**.
3. 🟡 **6 (Honigwaben)**, **7 (Fokus)**, **8 (mono)**, **9 (Token-Sync)** als Politur.

Drop-in für 1+2+4(+6): `krt-keycloak-tokens.css` ersetzt die `:root`/`@font-face`/
`body`-Blöcke beider Dateien 1:1.
