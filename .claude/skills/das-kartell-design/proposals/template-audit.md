# Template-Audit — Action-Hierarchie & Lesbarkeit (über `/missions/{id}` hinaus)

Geprüft im Repo `krt-iri/basetool` (Stand: dieser Branch): `index.html` (Dashboard),
`material-collection.html` (Inline-Edit-Grid), `admin/locations.html` (Admin-CRUD),
`error/403.html` (Fehlerseite) — als Stellvertreter für die übrigen Seiten.
Bewertet mit derselben Brille wie die mission-detail-Überarbeitung.

Bezug: die neuen System-Klassen/Tokens in `krt-components.css` + `colors_and_type.css`
(`.btn--cta`, `.btn-outline`, `.btn-ghost`, `.btn-quiet-danger`, `.panel-header`,
`.data-value`, neutrale `label`, hellgraue `th`). Siehe README → „Action hierarchy".

---

## Wiederkehrende Befunde (system-weit)

### A — Per-Template-Overrides heben die neue neutrale Label-Regel wieder auf ⛔ (hoch)
Mehrere Templates setzen Labels per Inline-`<style>` wieder auf Orange — das macht
die System-Änderung wirkungslos.
- `mission-detail.html`: `label { color: var(--color-primary) }` → **entfernen**.
- `admin/locations.html`: `.form-group label { color: var(--color-primary);
  font-family: var(--font-headline); font-size: 0.8rem }` → **entfernen**
  (erbt dann neutrale Lato-Labels aus dem System).
- Mutmaßlich gleich in weiteren `admin/*`-Seiten (gleiches Override-Pattern).

> **Aktion:** Repo-weit nach `label { ... color: var(--color-primary)` und
> `.form-group label` suchen und diese Overrides löschen.

### B — Ethnocentric (Display-Schrift) für Kleintext ⛔ (hoch, Lesbarkeit)
`admin/locations.html` rendert 0,8-rem-**Labels in `--font-headline` (Ethnocentric)**.
Die Display-Schrift ist für Versal-Überschriften gedacht und bei Kleingrößen schwer
lesbar. → Labels/Datentexte immer **Lato**; Ethnocentric nur für echte Headlines.

### C — CTA-Hierarchie noch nicht angewandt 🟠 (mittel)
Pro View sollte **eine** gefüllte Orange-CTA dominieren; Neben-/Utility-Aktionen
demoten.
- `index.html` (Dashboard): „Einsatz öffnen" nutzt `class="btn btn-primary"` —
  **`.btn-primary` existiert gar nicht** (No-Op, wirkt nur als `.btn`). → auf
  **`.btn btn--cta`** setzen (die eine Dashboard-CTA). „Als gelesen markieren"
  ist ebenfalls `.btn` (orange) und konkurriert → **`.btn-ghost`**.
- `admin/locations.html`: Toggle „Ein-/Ausblenden" ist gefüllt orange für eine
  risikoarme Aktion → **`.btn-ghost`** (oder `.btn-outline`).

### D — Datentexte/Key-Labels in Orange 🟠 (mittel)
- Dashboard „Nächste Mission": die `<strong>`-Keys im `.mission-info-grid` sind
  orange (aus `styles.css`). → Keys auf **`--color-gray-1`**, damit die **Werte**
  hervortreten (gleiches Prinzip wie Organisation-Panel).

### E — Duplizierte/uneinheitliche Komponenten 🟡 (mittel, Wartbarkeit)
- **Zwei Toast-Systeme:** `material-collection.html` definiert ein eigenes
  `.krt-toast` inline, daneben existiert `fragments/toast.html` **und** im
  Design-System `.notification-toast`. → auf **eine** Toast-Komponente konsolidieren.
- **Uneinheitliche Tabellen-Klassen:** mal `.data-table`, mal bare `<table>`, in
  mission-detail eigenes Markup. → eine kanonische Tabellen-Klasse nutzen.
- **Token-Wildwuchs:** `material-collection.html` referenziert `var(--color-text)`,
  das System kennt `--color-gray-1`. → Token-Namen vereinheitlichen.

### F — DRY/Inline-Stil-Wiederholung 🟡 (niedrig)
- `material-collection.html` setzt `font-family:'Ethnocentric'...` inline auf das
  `h1`, obwohl `h1` das bereits erbt. Redundante Inline-Styles entfernen.
- Wiederkehrende Inline-`style="min-height:44px; ..."` auf Buttons/Inputs → in
  System-Klassen ziehen (`.btn` hat bereits 44px Touch-Target).

---

## Positiv-Beispiele (so beibehalten) ✅
- **Fehlerseiten** (`error/403.html`): klare Hierarchie — rote Identität, Sci-Fi-Copy,
  **genau eine** CTA „Return to Base". Vorbildlich. (Nur optional: `.btn-return`
  durch System-Button ersetzen, rein zur Wartbarkeit.)
- **`material-collection.html`**: Inline-Edit-Grid mit Selects + optimistic locking,
  saubere 44px-Targets, dezente Toaststreifen. Inhaltlich stark; nur die
  Komponenten-Duplikate (E) angleichen.

---

## Priorisierte Reihenfolge für Claude Code
1. **A + B** (hoch): alle Inline-Label-Overrides entfernen → app-weit neutrale,
   lesbare Labels in einem Rutsch. Größter Effekt, geringes Risiko.
2. **C** (mittel): pro Seite die eine CTA als `.btn--cta`, Utility-Buttons → ghost;
   `.btn-primary`-No-Op in `index.html` fixen.
3. **D** (mittel): Key-Labels/Datentexte neutral, Werte hervorheben (`.data-value`).
4. **E** (Wartbarkeit): Toast/Tabellen/Token konsolidieren — als eigener Cleanup-PR.
5. **F** (niedrig): redundante Inline-Styles entfernen.

Jeweils kleine Commits + `./gradlew check`; visuell gegen `ui_kits/basetool/` und
`proposals/mission-detail-button-hierarchy.html` abgleichen. Detail-Mapping für die
mission-detail-Seite steht in `proposals/claude-code-auftrag.md`.

---

## Route-für-Route (die geprüften Seiten)

Geprüft per Code-Inspektion der echten Templates. Durchgängiges Muster auf **allen
Listen-Seiten**: die „Neu/Erstellen"-Aktion, die „Details/Open"-Zeilenbuttons **und**
der Filter-Button sind alle dasselbe gefüllte Orange `.btn` → kein klarer Primär-Fokus.

| Route | Template | Befund | Vorschlag |
| :-- | :-- | :-- | :-- |
| `/` (Start) | `index.html` | „Einsatz öffnen" = `btn btn-primary` (**`.btn-primary` existiert nicht** → No-Op); „Als gelesen markieren" gefüllt orange; Key-Labels orange | „Einsatz öffnen" → `.btn--cta`; „Als gelesen" → `.btn-ghost`; Key-`<strong>` → `--color-gray-1` |
| `/missions` | `missions.html` | „Create New" + Zeilen-„Open" + Reset alle orange/`.btn`; **keine** Inline-Label-Overrides (profitiert direkt) | „Create New" → `.btn--cta`; „Open" → `.btn-ghost`; „Reset" bleibt/`.btn-ghost` |
| `/operations` | `operations-index.html` | „Create" + „Details" + Modal-„Save" orange; Zeilen-„Delete" = `.btn-danger`; Reset `.btn-secondary` | „Create"/Modal-„Save" → `.btn--cta` (je 1 pro Kontext); „Details" → `.btn-ghost`; „Delete" → `.btn-quiet-danger` |
| `/operations/{id}` | `operation-detail.html` | **Klon von mission-detail** (gleiches Vollorange-`.col-header`, `mission-column`, `label{color:primary}`-Override) | **Identischer Fix** wie mission-detail: `.panel-header`, Button-Leiter, Label-Override entfernen |
| `/orders` | `orders-index.html` | „Neuer Auftrag" + „Filtern" + Zeilen-„Details" alle orange | „Neuer Auftrag" → `.btn--cta`; „Filtern"/„Details" → `.btn-ghost` |
| `/orders/{id}` | `orders-detail.html` (99 KB) | Eigenes Card-Layout (kein col-header-Klon); sehr viele `.btn` | Eine CTA pro Abschnitt (Speichern), Rest ghost/outline; Datentexte `.data-value` |
| `/refinery-orders` | `refinery-orders-index.html` | „Neuer Auftrag" + „Filtern" + „Details" alle orange | wie `/orders` |
| `/refinery-orders/{id}` | `refinery-orders-details.html` (49 KB) | `.form-group label`-Orange-Override **+** Inline-orange `<h3>`; eigenes Layout | Label-Override entfernen; `<h3>` neutral/Lato; eine CTA, Rest ghost |
| `/inventory` | `inventory-index.html` | „Mein Lager" **und** „Globales Lager" beide gefüllt orange (zwei gleichwertige Navi-Ziele, kein Fokus) | beide → `.btn-outline` (gleichwertig) **oder** „Mein Lager" `.btn--cta` + anderes ghost |
| `/inventory/my` | `inventory-my.html` (72 KB) | viele konkurrierende `.btn` (Neu erfassen, Bulk-Checkout, Zeilenaktionen, Notiz speichern, Modal-Bestätigen) | „Neuen Eintrag erfassen" → `.btn--cta`; Zeilen-/Bulk-Aktionen → `.btn-ghost`; destruktiv → `.btn-quiet-danger`; je Modal 1 CTA |
| `/inventory/all` | `inventory-admin.html` (71 KB) | analog `/inventory/my`, admin-dicht | gleiches Schema; Admin-Chrome (`accent-dark`) beibehalten |

### Querschnitt-Erkenntnisse
- **`operation-detail.html` ist ein mission-detail-Klon** → der bereits erstellte
  `claude-code-auftrag.md` gilt 1:1 auch hier (gleiche Klassen/Selektoren).
- **Listen-Seiten** lassen sich mit **einer** Regel sanieren: „Neu/Erstellen" = einzige
  `.btn--cta`, alle Zeilen-„Details/Open" + „Filtern"/„Reset" = `.btn-ghost`,
  destruktive Zeilenaktionen = `.btn-quiet-danger`.
- **`.btn-primary` ist projektweit ein toter Klassenname** (nur `index.html` gefunden,
  aber prüfenswert) — entweder als Alias für `.btn--cta` definieren oder ersetzen.
- **Label-Overrides** (Orange/Ethnocentric) sitzen in `mission-detail`,
  `operation-detail`, `refinery-orders-details`, `admin/locations` — repo-weit suchen
  und entfernen, damit die neutrale System-Regel greift.
