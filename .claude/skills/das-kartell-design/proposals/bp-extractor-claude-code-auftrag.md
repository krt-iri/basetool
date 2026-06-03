# Claude-Code-Auftrag — `basetool-bp-extractor` UX-Feinschliff (KRT-konform)

Repo: <https://github.com/krt-iri/basetool-bp-extractor> · Kotlin / Compose for
Desktop. Ziel: gezielte **Nutzerfreundlichkeits- und A11y-Verbesserungen** umsetzen,
ohne das Design zu verändern — die App ist bereits eine vorbildliche KRT-Umsetzung.

## Quelle der Wahrheit
Das `das-kartell-design`-Skill (`.claude/skills/das-kartell-design/`):
- `colors_and_type.css` / `krt-components.css` — Tokens, Button-Leiter, Danger-Border,
  `:focus-visible`-Idiom, `.data-value`, `.notification-toast`.
- README → „Action hierarchy". Voll-Audit: `proposals/bp-extractor-audit.md`.
  Visualisierung: `proposals/bp-extractor-audit-mock.html`.
Betroffene Dateien: `ui/KrtComponents.kt`, `ui/Theme.kt`, `ui/WindowChrome.kt`, `Main.kt`.

## Grundregeln
- **Nichts am Look ändern**: keine neuen Farben/Formen; nur `Krt.*`-Tokens verwenden,
  eckig bleiben, eine CTA pro Screen behalten.
- Kleine Commits, ein Schritt pro Block. Nach jedem Schritt `./gradlew check` (Tests,
  ktlint/detekt) und die App einmal manuell starten (`./gradlew run`).
- Bestehende i18n/Deutsch-Texte beibehalten; neue UI-Strings im selben Stil/Sprache.

---

## SCHRITT 1 🔴 — Tastatur-Fokus sichtbar machen (A11y)
Problem: alle Controls nutzen `clickable(..., indication = null)` → kein sichtbarer
Fokus für Tastatur-Nutzer.
- In `KrtComponents.kt` (`CtaButton`, `GhostButton`, `KrtCheckbox`) und
  `WindowChrome.kt` (`WindowControlButton`) den `MutableInteractionSource` zusätzlich
  auf **Fokus** auswerten: `val focused by interaction.collectIsFocusedAsState()`.
- Bei `focused` einen **2dp-Orange-Rahmen** (`Krt.Orange`) mit 2dp Offset zeichnen
  (analog `:focus-visible { outline: 2px solid … }` im System). Für `CtaButton` einen
  Border auf der inneren Box; für `GhostButton` Border-Farbe → Orange auch bei Fokus.
- `.focusable(interactionSource = interaction)` ergänzen, damit die Controls per Tab
  erreichbar sind. Tab-Reihenfolge prüfen (Felder → Durchsuchen → CTA → Ergebnis-Aktionen).
- Akzeptanz: Mit Tastatur (Tab/Shift+Tab/Space/Enter) ist jederzeit erkennbar, welches
  Element fokussiert ist; Enter/Space löst es aus.

## SCHRITT 2 🔴 — Determinierte Fortschrittsanzeige
Problem: `Main.kt` erhält `(done, total, current)`, zeigt aber einen unbestimmten
`CircularProgressIndicator`.
- `AppState` um `progressDone`/`progressTotal` (Int) erweitern; im Callback von
  `BlueprintExtractor.extract` setzen.
- Neue Composable `KrtProgressBar(done, total)` in `KrtComponents.kt`: 6dp hohe Leiste,
  Track `Krt.SurfaceInput` + 1dp `Krt.Gray3`-Border, Füllung `Krt.Orange`
  (`fraction = done/total`). Eckig, kein Rund.
- In `ExtractorScreen` den Spinner durch die Leiste ersetzen (Spinner nur als Fallback,
  solange `total == 0`). Statuszeile „Verarbeite Datei 8/13: …" bleibt.
- Akzeptanz: Während der Extraktion wächst die Leiste sichtbar mit den Dateien.

## SCHRITT 3 🟠 — Validierung sichtbar machen (CTA-Grund + Feldfehler)
Problem: CTA ist still deaktiviert; ungültiger Pfad meldet sich nur in der Statuszeile.
- `KrtTextField` um `isError: Boolean = false` erweitern; bei `true`
  `focused/unfocusedBorderColor = Krt.Danger` und ein optionales
  `supportingText`/Inline-Label darunter (`Krt.Danger`, mit „⚠ "-Glyph).
- In `AppState` pro Feld einen `channelError`/`outputError`-State; in `runExtraction`
  bzw. bei Eingabe setzen (z. B. Channel-Ordner existiert nicht → `channelError`).
- CTA-Variante A (bevorzugt): CTA **aktiv lassen**, beim Klick validieren und Felder
  markieren. Variante B: CTA disabled lassen, aber darunter einen `bodySmall`-Hinweis
  in `Gray2` zeigen, was fehlt.
- Akzeptanz: Ein falscher Pfad färbt das betreffende Feld rot mit Inline-Meldung; der
  Nutzer weiß ohne Raten, warum es nicht weitergeht.

## SCHRITT 4 🟠 — Folge-Aktionen nach Erfolg
Problem: nach dem JSON-Schreiben nur Text.
- Im Ergebnis-Panel (`hudBox`) eine Row mit zwei `GhostButton`:
  „Im Ordner anzeigen" → `java.awt.Desktop.getDesktop().open(outputFile.parentFile)`;
  „JSON öffnen" → `Desktop.open(outputFile)`. Auf Desktop-Support prüfen
  (`Desktop.isDesktopSupported()`), sonst Buttons ausblenden.
- Akzeptanz: Nach erfolgreicher Extraktion öffnen die Buttons Ordner bzw. Datei.

## SCHRITT 5 🟡 — Typo-/Titel-Politur
- `Theme.kt`: `headlineSmall` von 13sp → **15sp**, `letterSpacing` 0.04em → **0.05em**
  (System-Wert). Prüfen, dass Titelleiste + „ERGEBNIS" weiterhin gut sitzen.
- Titelleiste (`KrtTitleBar`) auf kurzen Titel umstellen (z. B. „Blueprint Extractor"),
  da `GreetingHeader` den vollen Namen trägt → keine Dopplung, kein Ellipsis.
- Optional: lange Pfade in Feldern per Tooltip (Vollpfad) oder Mitte-Kürzung lesbarer machen.

## SCHRITT 6 🟡 (optional) — Erfolgs-Toast
- Eine `KrtToast`-Composable nach Vorbild `.notification-toast` (Eckklammern + Bloom,
  Erfolg = Orange, Fehler = Danger) ergänzen und bei Abschluss kurz einblenden.
  Nur umsetzen, wenn es den Single-Screen-Flow nicht überlädt.

---

## NICHT ändern (bewusst gut)
- Fankit-Footer (`CommunityDisclaimerFooter`): Logo unverändert/volle Deckkraft,
  Trademark-Notice lesbar — **so lassen** (Fankit-Pflicht).
- Custom-Window-Chrome, CLI-Modus (`runCli`), `KrtDataStyle` mit `tnum`, alle Farben,
  Formen, die HUD-Box und das Wabenmuster.

## Definition of Done
1. Tastatur-Fokus auf jedem Control sichtbar; volle Tab-Bedienbarkeit.
2. Fortschritt determiniert (Datei x/y) während der Extraktion.
3. Ungültige Eingaben markieren das betroffene Feld rot + Inline-Meldung; CTA-Zustand
   ist nie „grundlos" deaktiviert.
4. Nach Erfolg „Im Ordner anzeigen" / „JSON öffnen" verfügbar.
5. Keine Stil-Regression: Farben/Form/Typo weiterhin KRT-konform; `./gradlew check` grün;
   Optik deckt sich mit `proposals/bp-extractor-audit-mock.html` (rechte Spalte).
