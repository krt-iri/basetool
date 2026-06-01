# Claude-Code-Auftrag — Profit Basetool app-weit am KRT-Design-System vereinheitlichen

Ziel: Das **gesamte** Frontend konsistent an das KRT-Design-System angleichen —
mit Fokus auf die **Action-Hierarchie** (primäre Aktionen wieder auffindbar machen)
und das Entfernen abweichender Inline-Stile. Auslöser war der Nutzer-Report
„`/missions/{id}` zu orange, ‚Anmelden' schwer zu finden"; die Lösung gilt app-weit.

## Quelle der Wahrheit
Dieses Skill (`.claude/skills/das-kartell-design/`):
- `colors_and_type.css` — Tokens, `@font-face`, neutrale `label`-Defaults.
- `krt-components.css` — Komponenten + **Button-Leiter** + `.panel-header` + `.data-value`.
- `README.md` → Abschnitt **„Action hierarchy"** (das Leit-Prinzip).
- Referenz-Optik: `ui_kits/basetool/` (interaktiv) und die Vorher/Nachher-Mocks
  `proposals/mission-detail-button-hierarchy.html`, `proposals/list-page-button-hierarchy.html`,
  `proposals/inventory-button-hierarchy.html`, `proposals/refinery-order-button-hierarchy.html`.
- Voll-Audit mit Route-Tabelle: `proposals/template-audit.md`.

## Leitprinzip (für jede Seite gleich)
1. **Eine gefüllte Orange-CTA pro Kontext** (`.btn--cta`) — die wichtigste Aktion
   (Neu/Erstellen/Speichern/Anmelden). Alles andere demoten.
2. Orange ist für **Aktion + Identität** (Logo, Badges, Headlines) — **nie** für
   Formular-Labels, Tabellentext oder reine Datenwerte.
3. Quadratische Ecken, HUD-Idiom, 44px Touch-Targets, Lato für Text / Ethnocentric
   nur für echte Headlines — wie gehabt.

## Button-Leiter (verbindlich)
| Klasse | Einsatz |
| :-- | :-- |
| `.btn .btn--cta` | die **eine** primäre Aktion pro View/Panel/Modal |
| `.btn .btn-success` | Status/Zustandswechsel (Check-In) |
| `.btn .btn-outline` | betonte Zweitaktion (z. B. „Crew zuweisen") |
| `.btn .btn-ghost` | wiederholte/Routine-Aktion (Details, Open, Edit, Filtern, Reset) |
| `.btn .btn-quiet-danger` | destruktiv (Delete, Entfernen) |

---

## ARBEITSWEISE
- **Kleine, prüfbare Commits**, ein Block pro Schritt unten.
- Nach jeder Änderung `./gradlew check` (Tests + Checkstyle/SpotBugs).
- Bestehende **i18n-, CSP-Nonce- und Responsive**-Regeln aus `AGENTS.md` einhalten;
  keine neuen Farben erfinden; nur Tokens/Klassen aus dem System verwenden.
- Nach jedem Block visuell gegen die Referenz (`ui_kits/basetool/` + Mocks) prüfen.

---

## SCHRITT 1 — System-Dateien übernehmen (Fundament)
Übernimm `krt-components.css` + `colors_and_type.css` aus dem Skill in das Frontend
(bzw. gleiche `static/css/styles.css` an deren Tokens/Klassen an). Neu/aktualisiert:
- Button-Klassen `.btn--cta`, `.btn-outline`, `.btn-ghost`, `.btn-quiet-danger`
- Komponente `.panel-header` (+ `.panel-count`, `.toggle-icon`)
- `.data-value` / `.data-value--mono`
- `label`, `.form-label`, `.form-label-sm` → `--color-gray-1`
- `th`-Textfarbe → `--color-gray-1` (2px-Orange-Unterstrich bleibt)
- Tokens `--action-primary/-emphasis/-neutral`, `--data-fg`
- **`.btn-primary`**: existiert nicht — entweder als Alias auf `.btn--cta` definieren
  **oder** alle Vorkommen ersetzen (siehe Schritt 2/Startseite).

## SCHRITT 2 — Globaler „Label- & Inline-Override-Sweep" (höchste Wirkung, geringes Risiko)
Repo-weit nach diesen Mustern suchen und entfernen, damit die System-Defaults greifen:
- `label { … color: var(--color-primary) }` und `.form-group label { … }` —
  **bekannt in**: `mission-detail.html`, `operation-detail.html`,
  `refinery-orders-details.html`, `admin/locations.html` (dort zusätzlich
  `font-family: var(--font-headline)` auf Labels → entfernen, Labels gehören in Lato).
- Inline `style="color: var(--color-primary)"` / `var(--color-accent-light)` auf
  **Datentexten** (Namen, Frequenzen, Werte) → durch Klasse `.data-value` ersetzen.
- Redundante Inline-`font-family:'Ethnocentric'`/`'Lato'` auf Elementen, die das
  bereits erben (z. B. `material-collection.html` h1) → entfernen.

## SCHRITT 3 — Listen-Seiten (eine Regel für alle)
Betrifft: `missions.html`, `operations-index.html`, `orders-index.html`,
`refinery-orders-index.html`. Referenz-Mock: `proposals/list-page-button-hierarchy.html`.
- „Neu/Erstellen" (`Create`, `Neuer Auftrag`, `operation.create`) → **`.btn--cta`**.
- Zeilen-„Details/Open" → `.btn-ghost`.
- „Filtern"/„Reset" → `.btn-ghost` (Reset darf `.btn-ghost` bleiben).
- Zeilen-/Modal-„Delete" → `.btn-quiet-danger`; im Lösch-Modal ist „Delete" die CTA.
- In Erstell-/Bearbeiten-Modals: „Save" → `.btn--cta`, „Cancel" → `.btn-ghost`.

## SCHRITT 4 — Detail-Seiten
- **`mission-detail.html`** + **`operation-detail.html`** (Klon, gleiche Selektoren):
  - `.col-header` (Vollorange-Rahmen+Text) → Optik der Komponente `.panel-header`
    (Surface-Fill + `border-left:4px` + helle `h2`; Chevron/Aktiv-State orange).
  - „Anmelden", „Speichern", „Hinzufügen" → `.btn--cta` (je 1 pro Panel).
  - „Crew zuweisen" → `.btn-outline`; alle „Edit"/„Check-Out" → `.btn-ghost`;
    „Check-In" bleibt `.btn-success`; alle „Delete/Entfernen" → `.btn-quiet-danger`.
  - Sub-Formular-Saves (Party-Lead, Owner, Manager) → `.btn-ghost`/`.btn-outline`,
    **nicht** `.btn--cta` (eine CTA pro Kontext).
  - Datenwerte (Party-Lead-/Owner-Name, Frequenzen) → `.data-value`.
- **`orders-detail.html`**, **`refinery-orders-details.html`** (eigenes Card-Layout):
  Referenz-Mock: `proposals/refinery-order-button-hierarchy.html`.
  - Pro Karte/Abschnitt eine `.btn--cta` (Speichern/Hauptaktion), Rest `.btn-ghost`/
    `.btn-outline`, destruktiv `.btn-quiet-danger`.
  - `refinery-orders-details.html`: Label-Override entfernen, inline-orange `<h3>` →
    neutral (Lato, `--color-gray-1`).
  - Datentexte → `.data-value`.

## SCHRITT 5 — Inventar & Hangar
Betrifft: `inventory-index.html`, `inventory-my.html`, `inventory-admin.html`,
`hangar.html`, `material-collection.html`, `personal-inventory*.html`.
Referenz-Mock: `proposals/inventory-button-hierarchy.html`.
- `inventory-index.html`: „Mein Lager" + „Globales Lager" sind gleichwertige Navi →
  beide `.btn-outline` (kein doppeltes Vollorange).
- `inventory-my.html` / `inventory-admin.html`: „Neuen Eintrag erfassen" → `.btn--cta`;
  Bulk-/Zeilenaktionen → `.btn-ghost`; destruktiv → `.btn-quiet-danger`; je Modal 1 CTA.
- `hangar.html`: „Add Ship" → `.btn--cta`; Zeilen-Edit → `.btn-ghost`, Delete → quiet.
- Admin-Chrome (`accent-dark`-Header im Admin) beibehalten.

## SCHRITT 6 — Komponenten konsolidieren (Wartbarkeit, eigener PR)
- **Toast**: ein System. `material-collection.html` definiert ein eigenes `.krt-toast`
  inline — auf `fragments/toast.html` bzw. `.notification-toast` vereinheitlichen.
- **Tabellen**: eine kanonische Klasse statt mal `.data-table`, mal bare `<table>`.
- **Token-Namen**: `var(--color-text)` → `--color-gray-1` (System-Token) angleichen.
- Wiederkehrende Inline-`min-height:44px`/Padding auf Buttons → in `.btn` belassen.

## SCHRITT 7 — Beibehalten (nicht „verschlimmbessern")
- **Fehlerseiten** (`error/403|404|500`): bereits vorbildlich (eine CTA, Sci-Fi-Copy).
  Nur optional `.btn-return` durch `.btn .btn-danger` ersetzen (rein DRY).
- Squadron-Badges, Status-Pills, Department-Tags, Logo-Regeln: unverändert.

---

## AKZEPTANZKRITERIEN (Definition of Done)
Pro Seite gilt sie als vereinheitlicht, wenn:
1. **Auf einen Blick** genau eine primäre Aktion erkennbar ist (eine gefüllte CTA).
2. Kein Formular-Label und kein reiner Datenwert orange ist.
3. Wiederholte Zeilenaktionen ruhig (ghost/quiet) sind — keine Orange-Kaskade.
4. Keine Inline-Stile mehr Tokens/Komponenten des Systems überschreiben.
5. Schrift: Ethnocentric nur in echten Headlines, sonst Lato.
6. `./gradlew check` grün; Optik deckt sich mit `ui_kits/basetool/` + den Mocks.

> Reihenfolge nach Wirkung/Risiko: **2 → 3 → 4 → 5 → 6**. Schritt 1 ist Voraussetzung.
> Schritt 2 (Override-Sweep) bringt app-weit den größten Lesbarkeitsgewinn bei
> minimalem Risiko und sollte zuerst gemerged werden.
