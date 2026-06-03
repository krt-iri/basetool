# Audit — `basetool-bp-extractor` gegen das KRT-Design-System

Geprüft: <https://github.com/krt-iri/basetool-bp-extractor> (`main`) — eine
Kotlin/Compose-Desktop-App (Star-Citizen-Blueprint-Extractor, Game.log → JSON).
Gelesen: `ui/Theme.kt`, `ui/KrtComponents.kt`, `ui/WindowChrome.kt`, `Main.kt`.
Referenz: das `das-kartell-design`-Skill (`colors_and_type.css`, `krt-components.css`,
README → „Action hierarchy"). Visualisierung: `bp-extractor-audit-mock.html`.

---

## Gesamturteil

**Vorbildliche Umsetzung.** Diese App ist die sauberste KRT-Implementierung, die ich
bisher gesehen habe — das Design-System ist praktisch 1:1 nach Compose portiert.
Die folgenden Punkte sind **Feinschliff/UX**, keine Compliance-Fehler.

---

## Design-System-Compliance — ✅ erfüllt

| Bereich | Status | Beleg im Code |
| :-- | :-- | :-- |
| **Farben** | ✅ exakt | `Theme.kt > Krt`: `Orange #E77E23`, Grau 1–4, `SurfaceInput #1C1C1C`, Semantik korrekt |
| **Typo** | ✅ | `Display = Audiowide`, `Lato` (Light/Regular/Bold); Headlines UPPERCASE durch Aufrufer |
| **Eckig** | ✅ | `Shapes(... = Square 0.dp)` global; `RectangleShape` auf Textfeldern |
| **HUD-Box** | ✅ | `Modifier.hudBox()` mit zwei diagonalen Orange-Eckklammern + Hairline |
| **Greeting** | ✅ | `GreetingHeader`: Orange-Akzentbalken links + dark→transparent-Fade |
| **Action-Hierarchie** | ✅ | genau **ein** `CtaButton` (gefüllt + Bloom), `GhostButton` für „Durchsuchen…" |
| **Neutrale Labels** | ✅ | `FieldLabel` ist `Gray1`, nicht orange — folgt der neuen Regel exakt |
| **Datenwerte** | ✅ | `KrtDataStyle` mit `tnum` für Ergebnis-Readout |
| **Fokus-Glow** | ✅ | Textfeld: `focusedBorderColor = Orange`, `cursorColor = Orange` |
| **Checkbox/Status** | ✅ | quadratische Checkbox (orange + schwarzer Haken), `StatusDot` = kleines Quadrat |
| **Wabenmuster** | ✅ | `rememberHoneycombPainter()` + `Modifier.tiled()` als HUD-Wash |
| **Touch-Targets** | ✅ | CTA `heightIn(min = 44.dp)`, Fenster-Controls 46×40 |
| **Custom-Chrome** | ✅ | `KrtTitleBar` ersetzt die weiße OS-Leiste; Orange-Hairline, eckig |
| **Fankit-Footer** | ✅ | `CommunityDisclaimerFooter` — Logo unverändert/volle Deckkraft + Trademark-Notice |

Kurz: Farben, Form, Typo, Komponenten und sogar das frisch hinzugefügte
Action-Hierarchie-Prinzip sind korrekt umgesetzt.

---

## Verbesserungsmöglichkeiten (UX / Nutzerfreundlichkeit)

Priorität: 🔴 hoch · 🟠 mittel · 🟡 niedrig. Visualisiert in `bp-extractor-audit-mock.html`.

### 🔴 1 — Tastatur-Fokus unsichtbar (A11y)
Alle interaktiven Elemente nutzen `clickable(..., indication = null)`. Das entfernt
**jedes** visuelle Feedback inkl. Tastatur-Fokus. Hover-States existieren (Maus), aber
Tastatur-Nutzer sehen nicht, welches Element fokussiert ist.
→ **Fix:** einen Fokus-Indikator ergänzen (z. B. 2dp-Orange-Border bei
`focusedState`, analog `:focus-visible` im CSS-System). Betrifft `CtaButton`,
`GhostButton`, `KrtCheckbox`, `WindowControlButton`.

### 🔴 2 — Unbestimmter Fortschritt trotz bekannter Datenmenge
`runExtraction` bekommt `(done, total, current)` und zeigt es als Text — aber die
Anzeige ist ein **unbestimmter** `CircularProgressIndicator`. Der Nutzer kann nicht
abschätzen, wie lange es dauert.
→ **Fix:** determinierte Fortschrittsleiste (`done/total`) in Orange auf `SurfaceInput`.
Passt zur HUD-Optik und nutzt vorhandene Daten. (Mock: Balken „Datei 8/13".)

### 🟠 3 — CTA deaktiviert ohne Begründung
`CtaButton` ist disabled, solange `channelFolder`/`outputFile` leer sind. Ein
ausgegrauter Button ohne Hinweis lässt Nutzer rätseln, was fehlt.
→ **Fix:** entweder einen kurzen Hinweis darunter („Bitte Channel-Ordner &
Ziel-Pfad wählen") **oder** CTA aktiv lassen und beim Klick validieren + Feldfehler
markieren (siehe 4). Best Practice: nie „still" deaktivieren.

### 🟠 4 — Ungültige Pfade ohne Feld-Feedback
Existiert der Channel-Ordner nicht, geht die Meldung nur in die **Statuszeile**; das
verursachende Textfeld bleibt unmarkiert. Das System kennt den Danger-Border.
→ **Fix:** betroffenes `KrtTextField` rot umranden + Inline-Fehlermeldung darunter
(Danger `#A3000A`). Eine `isError`-Variante des Feldes ergänzen.

### 🟠 5 — Keine Folge-Aktionen nach Erfolg
Nach dem Schreiben der JSON gibt es nur Text. Auf dem Desktop erwartet man
„Im Ordner anzeigen" / „Datei öffnen".
→ **Fix:** zwei `GhostButton` im Ergebnis-Panel („Im Ordner anzeigen" via
`Desktop.open(parent)`, „JSON öffnen"). Hebt die Nützlichkeit ohne Stil-Bruch.

### 🟡 6 — Audiowide bei sehr kleinen Größen
`headlineSmall = 13sp` (Audiowide) für Titelleiste und „ERGEBNIS"-Überschrift.
Audiowide ist eine breite Display-Schrift; bei 13sp leidet die Lesbarkeit, und die
Titelleiste muss per Ellipsis kürzen.
→ **Fix:** `headlineSmall` auf ~15sp anheben; Titelleiste kürzer fassen (z. B. nur
„Blueprint Extractor"), da das Greeting den vollen Titel ohnehin trägt → keine
Dopplung, kein Ellipsis. Tracking 0.04em → 0.05em (System-Wert) angleichen.

### 🟡 7 — Titel doppelt
Titelleiste **und** `GreetingHeader` zeigen „Basetool Blueprint Extractor". Auf
kleinem Fenster wirkt das redundant.
→ **Fix:** Titelleiste knapp halten, Greeting trägt den vollen Namen + Subtitle.

### 🟡 8 — Lange Pfade schwer prüfbar
Einzeilige Felder mit langen Windows-Pfaden zeigen das **Ende** (den eigentlich
wichtigen Teil) nicht. → Optional: Tooltip mit Vollpfad, oder Pfadmitte kürzen
(`C:\…\StarCitizen\LIVE`).

### 🟡 9 — Erfolg ohne Toast
Das System hat `.notification-toast` (Eckklammern + Bloom). Hier meldet sich Erfolg
nur via Statuszeile/Panel. Für eine Einzel-Screen-App vertretbar; ein kurzer
Erfolgs-Toast wäre die markentreue Kür. Optional.

---

## Nicht ändern (bewusst gut gelöst) ✅
- **Fankit-Footer** dauerhaft sichtbar, Logo unverändert/volle Deckkraft, Trademark
  lesbar (`Gray1`/`Gray4`, ≥13sp) — exakt nach Fankit-Vorgaben. Belassen.
- **Custom-Window-Chrome** statt weißer OS-Leiste — richtig fürs dunkle HUD.
- **CLI-Modus** (`Main.kt > runCli`) inkl. Trademark-Ausgabe — saubere Doppel-Nutzung.
- **`KrtDataStyle` mit `tnum`** für Ergebniszahlen — genau das Daten-Prinzip.

---

## Priorisierte Reihenfolge
1. **🔴 1 (Fokus-Sichtbarkeit)** — A11y-Grundlage, betrifft alle Controls.
2. **🔴 2 (determinierter Fortschritt)** — größter wahrgenommener UX-Gewinn, Daten vorhanden.
3. **🟠 3 + 4 (Validierung sichtbar machen)** — gemeinsam umsetzen (CTA-Grund + Feldfehler).
4. **🟠 5 (Folge-Aktionen)** — kleiner Aufwand, hoher Alltagsnutzen.
5. **🟡 6–9 (Politur)** — Schriftgröße/Titel/Pfade/Toast als Sammel-PR.

Alle Vorschläge bleiben strikt im KRT-System (vorhandene Tokens/Komponenten); kein
neuer Stil, keine neuen Farben. Fix 4 nutzt den bereits definierten Danger-Border,
Fix 1 das vorhandene `:focus-visible`-Idiom, Fix 2/5 vorhandene Orange-/Ghost-Muster.
