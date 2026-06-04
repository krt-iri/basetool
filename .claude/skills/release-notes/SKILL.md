---
name: release-notes
description: Use this skill to write user-facing release notes / "Was ist neu" / Änderungsübersicht / Patch Notes / Update-Ankündigung for the Profit Basetool app, starting from a given point in time (a date, a git tag like v0.3.40, a release, or a commit). It turns the technical German CHANGELOG.md and git history into friendly, non-technical release notes for the squadron's normal members, grouped into Neu / Verbesserungen / Fehlerbehebungen. As part of the same run it also tidies CHANGELOG.md itself: entries still sitting under `[Unreleased]` that have already shipped under a git tag (`vX.Y.Z`) are moved into that tag's own dated section. Trigger this whenever the user wants to communicate recent changes to end users — e.g. "schreib Release Notes seit v0.3.40", "was ist neu seit dem 15.05 für die Nutzer", "fasse die letzten Änderungen für die Mitglieder zusammen", "Update-Ankündigung für die letzten zwei Wochen" — even when they don't say the words "release notes". When the user gives no start point at all, the skill automatically resumes from where the last release notes ended, using a local, git-ignored progress marker (.release-notes-state.json) so nobody has to remember the last covered point.
user-invocable: true
---

# Release Notes für das Profit Basetool

Du verwandelst die **technische Änderungshistorie** (CHANGELOG.md + Git-Log) in
**Release Notes für ganz normale Nutzer** der App — Staffel-Mitglieder, die kein
Wort Code lesen und nicht wissen, was eine „Migration", ein „Endpoint" oder ein
„Token" ist. Sie wollen nur wissen: **Was ist neu, was ist besser, was wurde
repariert** — und was das für sie im Alltag bedeutet.

Das Ergebnis ist ein fertiges Markdown-Dokument auf **Deutsch**, das man 1:1 in
eine Ankündigung, ins Wiki oder in einen GitHub-Release kopieren kann.

## Eingabe: ab wann?

Die einzige Pflichtangabe ist der **Startpunkt** — ab wann Änderungen betrachtet
werden sollen. Er kann in mehreren Formen kommen; erkenne die Form und behandle sie
entsprechend:

- **Datum** (`2026-05-15`, „seit dem 15. Mai", „letzte zwei Wochen") → Zeitfenster
  ab Tagesbeginn (00:00).
- **Datum mit Uhrzeit** (`2026-05-15 14:30`, „seit gestern 18 Uhr", „ab heute
  Mittag") → Zeitfenster ab genau dieser Minute. Die Uhrzeit (Stunden:Minuten,
  Sekunden optional) ist **immer optional** und verfeinert nur die Untergrenze;
  der Titel bleibt datumsgenau (`TT.MM.`). Übergib sie als **ein** Argument — in
  Anführungszeichen (`--since "2026-05-15 14:30"`) oder in der `T`-Form ohne
  Leerzeichen (`--since 2026-05-15T14:30`). Sag der Nutzer eine Uhrzeit ohne Datum
  („seit 14 Uhr"), kläre das gemeinte Datum kurz — git braucht beides.
- **Git-Tag / Version** (`v0.3.40`, „seit dem letzten Release") → präziser Bereich
  `v0.3.40..HEAD`. Das ist die genaueste Form. Wenn der Nutzer „letztes Release"
  sagt, hol das neueste Tag mit `git tag --sort=-creatordate | head -1`.
- **Commit-SHA** → Bereich `<sha>..HEAD`.

Optional: ein **Endpunkt** (Standard = jetzt / `HEAD`; akzeptiert dieselben Formen
wie der Startpunkt — Datum, Datum mit Uhrzeit oder Ref) und ein **Versions-Label**
für die optionale Unterzeile (z. B. die geplante neue Versionsnummer). Der Titel
selbst ist immer das Datums-Fenster (siehe Ausgabeformat), unabhängig vom Label.

**Fehlt der Startpunkt komplett, ist das der Normalfall** — und du musst nicht
nachfragen: Der Skill setzt automatisch dort fort, wo die letzten Release Notes
endeten. Er liest dazu einen **lokalen Fortschrittsmarker** (siehe nächster
Abschnitt) und sammelt alles, was seit diesem Punkt dazugekommen ist. **Nur wenn
es noch keinen Marker gibt** (allererster Lauf), frag genau einmal kurz nach („Ab
welchem Datum oder welcher Version?") und schlage das letzte Tag als Default vor —
rate nicht. Das Hilfsskript sagt dir von selbst, welcher der beiden Fälle vorliegt.

## Automatisches Fortschritts-Tracking (lokal, nie committet)

Damit niemand sich merken muss, bis wohin die letzten Release Notes gingen, merkt
sich der Skill das selbst — in einer **lokalen** Datei `.release-notes-state.json`
im Repo-Wurzelverzeichnis. Sie hält den Commit fest, bis zu dem zuletzt Notes
erstellt wurden, dazu das neueste darin enthaltene Release-Tag und dessen Datum.

- **Die Datei wird nie eingecheckt.** Sie steht in `.gitignore`, und jedes
  Schreiben trägt den Eintrag erneut ein und prüft per git, dass die Datei
  wirklich ignoriert wird. Es ist reines lokales Tracking — beliebig löschbar, geht
  nie ins Repo.
- **Gelesen wird automatisch:** Läuft `gather_changes.py` ohne `--since`, nimmt es
  den gespeicherten Commit als Startpunkt (`<Commit>..HEAD`) und blendet im
  Changelog genau die Release-Abschnitte ein, die nach diesem Punkt geschnitten
  wurden. Gibt es noch keinen Marker, bricht es mit einem Hinweis ab (frag dann den
  Nutzer, siehe oben) — es rät nie einen Startpunkt.
- **Fortgeschrieben wird in einem eigenen, bewussten Schritt ganz am Ende**
  (Schritt 7), erst nachdem die Notes geschrieben sind. So wandert der Marker nie
  weiter, wenn ein Lauf abgebrochen wird, und er geht ausschließlich vorwärts.

Den aktuellen Marker nur ansehen (ändert nichts):

```bash
python .claude/skills/release-notes/scripts/track_release_notes.py --show
```

## Schritt 1 — Rohmaterial sammeln

Führe das Hilfsskript vom Repo-Wurzelverzeichnis aus. Es legt beide Quellen
nebeneinander: den relevanten Changelog-Ausschnitt (den `[Unreleased]`-Block
**plus** die zum Fenster gehörenden, datierten `## [vX.Y.Z]`-Abschnitte — die
reichhaltigsten Beschreibungen) und das nach Commit-Typ sortierte Git-Log (das
das Zeitfenster sauber abgrenzt).

```bash
# Normalfall ohne Startpunkt — setzt am lokalen Fortschrittsmarker fort:
python .claude/skills/release-notes/scripts/gather_changes.py
# expliziter Startpunkt (Datum oder Tag):
python .claude/skills/release-notes/scripts/gather_changes.py --since <DATUM-ODER-TAG>
# mit Uhrzeit (ein Argument, daher quoten oder T-Form):
python .claude/skills/release-notes/scripts/gather_changes.py --since "2026-05-15 14:30"
# optional: --until <DATUM-ODER-REF, auch mit Uhrzeit>   --repo <pfad>
```

> Auf diesem Windows-Rechner ggf. `python` statt `python3`, und sicherstellen,
> dass das aktuelle Verzeichnis das Repo-Wurzelverzeichnis ist (dort liegt
> CHANGELOG.md). Notfalls geht es auch von Hand: CHANGELOG.md lesen +
> `git log --no-merges --since="<datum>" --pretty="%h %ad %s" --date=short`
> (das `--since` von git nimmt auch eine Uhrzeit, z. B. `--since="2026-05-15 14:30"`)
> bzw. `git log --no-merges <tag>..HEAD ...`.

**Warum zwei Quellen — und warum das Git-Log den Ton angibt?** Die CHANGELOG.md
beschreibt jede Änderung ausführlich und benutzernah. Ist der Changelog
**nachgeführt** (siehe Schritt 6), liegen veröffentlichte Einträge bereits unter
ihrem datierten `## [vX.Y.Z]`-Abschnitt, und `## [Unreleased]` enthält nur noch
das wirklich noch nicht Veröffentlichte — das Skript schneidet dir genau die zum
Fenster passenden Abschnitte heraus. **War der Changelog länger nicht nachgeführt**
(historisch lag hier praktisch die gesamte Historie unter `## [Unreleased]`), kann
der `[Unreleased]`-Block noch riesig sein und Einträge weit vor deinem Startpunkt
enthalten. In **beiden** Fällen gibt das **Git-Log den Umfang vor**: es grenzt das
Fenster exakt ab und signalisiert über die Commit-Typen `feat`/`fix`/`perf` vs.
`chore`/`refactor`/`test`/`docs`, was überhaupt nutzerrelevant ist. Nimm aus dem
Changelog nur die Einträge, die zu den Commits deines Fensters passen — **kopiere
nie blind einen ganzen Block**.

**Achtung Release-Kadenz:** Hier werden mehrmals am Tag Tags geschnitten
(`v0.3.40`, `v0.3.41`, `v0.3.42` am selben Tag). „Seit dem letzten Release" kann
also fast leer sein. Liefert dein Bereich auffällig wenige Commits, frag nach, ob
ein größeres Fenster (Datum statt Tag) gemeint ist.

## Schritt 2 — Auf das Nutzersichtbare eindampfen

Das ist die wichtigste Filterstufe. Eine Release Note ist **kein** Commit-Log.
Behalte nur, was ein Nutzer **sehen, anklicken oder anders erleben** kann.

**Behalten** (nutzersichtbar):
- Neue Seiten, Buttons, Felder, Filter, Such- und Importfunktionen.
- Geändertes Verhalten, das auffällt: andere Spalten, neue Auswahl, anderer Ablauf.
- Behobene Fehler, die jemandem im Alltag begegnet sind (etwas war kaputt, leer,
  falsch, langsam — jetzt geht es).
- Performance **nur**, wenn sie spürbar ist („lädt jetzt flüssig", „kein Einfrieren
  mehr") — und dann ohne die technische Erklärung.

**Weglassen** (intern, für Nutzer unsichtbar):
- Datenbank-Migrationen (`V120`, „Spalte droppen", „NOT NULL"), Refactorings,
  Code-Aufräumarbeiten, Umbenennungen von Tokens/Variablen/Klassen.
- Abhängigkeits-Updates (`chore(deps)`, „bump … from x to y"), Test-, CI-, Build-
  und Lint-Änderungen, SBOM, Dokumentation.
- Sicherheits-/Architektur-Härtung ohne sichtbaren Effekt (CSP, Locking,
  Transaktionen, ThreadLocal/Reactor, MDC-Logging).
- Alles, was in der CHANGELOG selbst als unsichtbar markiert ist — Signalwörter:
  **„Rendering unverändert", „keine sichtbare Änderung", „rein interne", „reine
  interne Aufräumarbeit", „Verhalten unverändert", „Wire-Shape unverändert",
  „ships dark" / Feature-Flag Standard aus.** Solche Einträge fliegen raus, egal
  wie groß der Diff war.

Im Zweifel gilt: **Würde ein Mitglied den Unterschied bemerken, ohne dass man es
ihm erklärt? Wenn nein → weglassen.** Lieber zehn ehrliche, relevante Zeilen als
fünfzig, die niemand versteht.

## Schritt 3 — Technik in Klartext übersetzen

Was die Filterung überlebt, wird **vom Code-Jargon befreit**. Entferne aus jeder
Zeile restlos:

- Pfade & Endpoints: `/api/v1/...`, `GET/POST/PUT/PATCH`, `/orders/{id}`.
- Migrations- und Versionscodes: `V131`, Tabellen-/Spaltennamen, `@PreAuthorize`.
- Framework-/Technik-Namen: Thymeleaf, Hibernate, Jackson, Reactor, WebClient,
  Spring, Feature-Flag-Schlüssel (`krt.scwiki.…`), HTTP-Codes (`400/403/409/500`).
- Datei- und Klassennamen, SQL, CSS-/Token-Namen, PR-/Issue-Nummern (`#372`).
- Die **Ursachenанalyse** eines Bugfixes. Nutzer interessiert *was wieder geht*,
  nicht *warum es kaputt war*. (Eine ganz kurze Was-war-kaputt-Einordnung ist ok,
  die technische Wurzel nie.)

Nenne stattdessen die **Dinge, die der Nutzer in der App sieht**: Seitennamen wie
„Hangar", „Aufträge", „Persönliches Inventar", „Material-Übersicht"; Buttons und
Felder mit ihrer sichtbaren Beschriftung.

**Vorher → Nachher (echte Beispiele aus diesem Projekt):**

Neues Feature — Interna gestrichen, Nutzen voran:
> ❌ Roh: „Hangar (`/hangar`) und Staffelübersicht (`/hangar/squadron`) haben jetzt
> eine Live-Suche nach Schiffstyp … rein clientseitig, kein Reload … Lupen-Icon, Fokus-Glow."
> ✅ Note: **„Schiffe schneller finden:** Im Hangar und in der Staffelübersicht
> lässt sich jetzt direkt über der Tabelle nach einem Schiffstyp suchen — die Liste
> filtert sich sofort beim Tippen."

Bugfix — Ursache weg, Ergebnis bleibt:
> ❌ Roh: „Auftragsdetails: Status-, Übergabe- und Report-Toasts erscheinen wieder.
> Die Meldungen riefen ein nicht definiertes `showToast(...)` auf und warfen still
> `ReferenceError` …"
> ✅ Note: **„Auftragsdetails:** Status-, Übergabe- und Report-Meldungen werden
> wieder zuverlässig angezeigt."

Spürbare Performance — Technik raus, Erlebnis rein (samt sichtbarem Hinweis):
> ❌ Roh: „Material-Übersicht … virtuelles Scrollen … JSON … 16 MB auf 64 MB …"
> ✅ Note: **„Material-Übersicht lädt flüssig:** Die große Übersicht blockiert den
> Browser beim Öffnen nicht mehr und lässt sich ruckelfrei durchscrollen — auch bei
> sehr vielen Einträgen. Hinweis: Die Preise in der Übersicht können einem
> Aktualisierungslauf bis zu 10 Minuten nachlaufen; die Detailseite bleibt
> tagesaktuell."

Reine Token-Umbenennung — komplett raus:
> ❌ Roh: „Bereichsfarben-Tokens in `styles.css` umbenannt (Rendering unverändert)."
> ✅ → **weglassen** (für Nutzer unsichtbar).

## Schritt 4 — Bündeln, ordnen, zusammenfassen

- **Drei Rubriken**, in dieser Reihenfolge: **Neu** (neue Funktionen) →
  **Verbesserungen** (geändertes/aufgewertetes Verhalten) → **Fehlerbehebungen**.
  Leere Rubriken weglassen.
- **Zusammenführen:** Mehrere Commits/Einträge, die für den Nutzer *eine* Sache
  sind, werden *eine* Zeile. (Eine Funktion, die über fünf PRs kam, ist trotzdem
  ein Punkt.) Umgekehrt nie eine Liste technischer Einzel-Fixes 1:1 abschreiben.
- **Wichtigstes zuerst** innerhalb jeder Rubrik. Bei vielen Punkten optional ein
  kurzer **Highlights**-Absatz ganz oben (Fließtext, 1–3 Sätze, **kein**
  Aufzählungspunkt) für die größten Neuerungen — genaue Form siehe
  »Ausgabeformat → Verbindliche Stil- und Formatregeln«.
- **Nach Bereich gruppieren**, wenn die Liste lang wird (z. B. mehrere Auftrags-
  Punkte zusammen), damit sie scanbar bleibt.

## Schritt 5 — Schreiben: Ton & Sprache

- **Sprache: Deutsch, mit korrekten Umlauten.** Schreibe echte Umlaute und ß als
  **UTF-8-Zeichen**: `ä ö ü Ä Ö Ü ß`. **Niemals** die ASCII-Ersatzschreibweise
  `ue/oe/ae/ss` — also „für", „Aufträge", „Größe", „schließt", nicht „fuer",
  „Auftraege", „Groesse", „schliesst". Da dies Markdown ist (nicht `.properties`),
  niemals `\uXXXX`. **Achtung Quelldaten:** Ältere CHANGELOG-Einträge enthalten
  vereinzelt kaputt kodierte Umlaute (Mojibake wie `QualitÃ¤t`, `StÃ¼ck`, `groÃŸ`).
  Übernimm so etwas **nie** ungeprüft, sondern schreibe das richtige Zeichen
  (`Qualität`, `Stück`, `groß`). Speichere die Ausgabedatei als UTF-8.
- **Ansprache: unpersönlich, keine direkte Anrede — und niemals „du".** Das Subjekt
  ist die Funktion oder die Rolle, nicht der Leser: „… **lässt sich** jetzt …",
  „**Mitglieder/Offiziere/Logistiker können** jetzt …", „**Es gibt jetzt** …".
  Vermeide sowohl die Du-Form (**„du kannst"**, **„dein"**) als auch das direkte
  **„Sie"** — die sachlich-neutrale Form trägt die Notes. Beachte: „können" bleibt
  erlaubt, solange ein Subjekt davorsteht (eine Rolle, nicht der Leser).
  - ❌ „Du kannst jetzt Item-Aufträge bearbeiten." (Du-Form)
  - ❌ „Sie können jetzt Item-Aufträge bearbeiten." (direkte Anrede)
  - ✅ „Item-Aufträge lassen sich jetzt bearbeiten, solange noch keine Übergabe erfolgt ist."
  - ✅ „Logistiker können Items jetzt stückweise übergeben."
- **Nutzen zuerst, dann Detail.** Beginne mit dem, was die Person davon hat, nicht
  mit dem Mechanismus.
- **Aktiv, Präsens, kurz.** Ein Gedanke pro Punkt. Jeder Punkt beginnt mit einem
  **fett gesetzten Schlagwort/Bereich**, danach ein bis zwei Sätze.
- **Niemals Emojis.** Verwende in den Release Notes **kein einziges Emoji** — weder
  in Überschriften noch in Aufzählungspunkten noch im Fließtext (also kein ✨, 🔧,
  🐛, ✅, 🚀 o. Ä.). Rubriken und Punkte stehen rein als Text; das ist die
  Marken-Designregel (die App führt bewusst keine Emojis). Auch kein Sci-Fi-Slang —
  freundlich-sachlich und gut lesbar. Bereichs-/Seitennamen so, wie sie in der App
  stehen.

## Ausgabeformat

Liefere die Release Notes als ein zusammenhängendes Markdown-Dokument nach dieser
Vorlage (leere Abschnitte weglassen). **Die allererste Zeile ist immer ein Titel in
genau dieser Form:**

    # Release Notes (TT.MM. → TT.MM.)

Das **linke** Datum ist der Startpunkt des Fensters, das **rechte** das Datum der
letzten enthaltenen Änderung — beide im Format `TT.MM.` (mit Punkt am Ende), mit
dem Pfeil „→" dazwischen. Beispiel: **`Release Notes (31.05. → 02.06.)`**. Das
Hilfsskript gibt diesen Titel oben als `SUGGESTED TITLE` bereits fertig aus —
übernimm ihn wörtlich (nicht „01.06.-02.06." o. Ä., nicht ohne Pfeil, nicht mit
Jahr).

### Verbindliche Stil- und Formatregeln

Damit jede Release Note **immer gleich aussieht**, gelten diese Format-Vorgaben
fest — nicht von Lauf zu Lauf abweichen:

- **Überschriften-Ebenen (die „Schriftgrößen"):** Genau **eine** H1 (`#`) — der
  Titel, immer die erste Zeile. Jede Rubrik ist eine **H2** (`##`), mit exakt
  diesem Namen und in dieser Reihenfolge: `## Highlights`, `## Neu`,
  `## Verbesserungen`, `## Fehlerbehebungen` (Rubriknamen nie übersetzen oder
  umbenennen). **Keine H3 oder tiefer.** Eine lange Rubrik wird nicht über weitere
  Überschriften untergliedert, sondern über die fett gesetzten Schlagwörter der
  Punkte — so bleibt die Größenhierarchie in jeder Note identisch.
- **Optionale Unterzeile:** direkt unter dem Titel eine **kursive** Zeile
  `_Version <Label> · Stand <TT.MM.JJJJ>_`. Nur wenn ein Versions-Label vorliegt,
  sonst komplett weglassen (keine leere Platzhalterzeile).
- **Highlights = Fließtext-Absatz, kein Aufzählungspunkt.** Unter `## Highlights`
  steht ein kurzer Absatz aus 1–3 ganzen Sätzen, der die größten Neuerungen nennt;
  die wichtigsten Bereiche darin **fett**. Nur bei längeren Notes (Faustregel: ab
  etwa sechs Punkten insgesamt), bei kurzen Notes die ganze Rubrik weglassen.
- **Rubriken-Inhalt = flache Strichliste.** Neu, Verbesserungen und
  Fehlerbehebungen sind reine Aufzählungen mit `- ` (Bindestrich + Leerzeichen; nie
  `*`, `+` oder Nummern), ohne verschachtelte Unterpunkte — ein Gedanke pro Zeile.
- **Punkt-Aufbau:** Jeder Punkt beginnt mit einem **fett gesetzten Schlagwort samt
  Doppelpunkt**, dann ein bis zwei ganze Sätze, z. B.
  `- **Organigramm:** Eine neue Seite zeigt …`. Der Doppelpunkt steht **innerhalb**
  der Fettung (`**Organigramm:**`); das Schlagwort ist ein Substantiv/Bereichsname,
  kein Verb. Höchstens zwei Sätze pro Punkt — was länger wird, kürzen oder bündeln.
- **Satzzeichen & Typografie:** ganze Sätze, die mit **Punkt** enden;
  Gedankenstrich als Halbgeviertstrich „–" mit Leerzeichen (nicht `--`); deutsche
  Anführungszeichen „…" (keine geraden oder englischen). App-Begriffe (Seiten,
  Buttons, Felder) genau in ihrer Oberflächen-Schreibweise.
- **Leerzeilen & Verbotenes:** genau eine Leerzeile zwischen Titel, Unterzeile,
  Rubriken-Überschriften und Listen. **Keine** Trennlinien (`---`) zwischen den
  Rubriken, **keine** Tabellen, Bilder, Zitatblöcke oder Inline-Code (Backticks) —
  der Nutzertext enthält keinen Code — und **kein einziges Emoji** (siehe Schritt 5).

```markdown
# Release Notes (TT.MM. → TT.MM.)

_Version <Label> · Stand <TT.MM.JJJJ>_   <!-- optionale Unterzeile, nur falls ein Versions-Label vorliegt -->

## Highlights
<Kurzer Fließtext-Absatz, kein Aufzählungspunkt: 1–3 ganze Sätze zu den größten Neuerungen, wichtigste Bereiche **fett** — nur bei längeren Notes.>

## Neu
- **<Funktion/Bereich>:** <Was jetzt möglich ist, in einfachen Worten>.

## Verbesserungen
- **<Bereich>:** <Was jetzt besser/anders ist>.

## Fehlerbehebungen
- **<Bereich>:** <Was wieder zuverlässig funktioniert>.
```

Zeig das Dokument direkt in der Antwort. Biete an, es zu speichern (z. B. unter
`docs/` oder ins Wiki) oder als GitHub-Release-Text aufzubereiten — aber lege nur
Dateien an oder poste nichts nach außen, wenn der Nutzer das ausdrücklich will.

## Schritt 6 — CHANGELOG nachführen (released-Abschnitte schneiden)

Zum Erstellen der Release Notes gehört, die **CHANGELOG.md selbst aufzuräumen**:
Einträge, die noch unter `## [Unreleased]` stehen, aber inzwischen unter einem
Git-Tag (`vX.Y.Z`) veröffentlicht wurden, gehören in den Abschnitt **dieses
Tags** — nicht weiter ins „Unreleased". So spiegelt der Changelog die echten
Releases wider, und `[Unreleased]` enthält nur noch wirklich Unveröffentlichtes.

Das macht ein zweites Hilfsskript mechanisch. **Immer erst der Probelauf**, den
Bericht prüfen, dann schreiben:

```bash
python .claude/skills/release-notes/scripts/reconcile_changelog.py            # Probelauf (schreibt nichts)
python .claude/skills/release-notes/scripts/reconcile_changelog.py --write    # CHANGELOG.md umschreiben
# optional: --repo <pfad>   --rev <ref, Default HEAD>   --repo-url <github-basis>
```

**Wie es zuordnet:** Jeder Eintrag (ein `- `-Punkt samt eingerückter
Unterpunkte/Folgezeilen) wird per `git blame` auf den Commit zurückgeführt, der
ihn geschrieben hat; dieser Commit wird auf das **früheste** ihn enthaltende,
wohlgeformte `vN.N.N`-Tag abgebildet — also das Release, in dem er erstmals
ausgeliefert wurde. Mehrzeilige Einträge nehmen das **kleinste** Tag über alle
ihre Zeilen (eine spätere Korrektur an einer Zeile schiebt den Eintrag nicht in
ein neueres Release). Einträge, deren Commit in **keinem** Tag liegt, bleiben in
`[Unreleased]`. Die `### `-Rubriken werden auf ihr Grundwort normalisiert
(`### Changed (Paket 3A …)` → `### Changed`) und je Release in der Reihenfolge
Added → Changed → Deprecated → Removed → Fixed → Security (Sonstige danach)
gebündelt.

**Eigenschaften, auf die Verlass ist:**
- **Verlustfrei am Text:** Der Wortlaut jedes Punktes bleibt 1:1 erhalten; nur
  Gruppierung und Rubriken-Köpfe ändern sich. Der Probelauf meldet, falls eine
  Nicht-Leerzeile keinem Eintrag zugeordnet werden konnte, und **verweigert dann
  das Schreiben** — nichts geht still verloren.
- **Idempotent:** Erneutes Ausführen auf einem bereits nachgeführten Changelog
  ändert nichts (leeres `[Unreleased]` → keine Verschiebung).
- **Nur saubere Tags:** Tippfehler-Tags wie `v-0.2.23` oder `v.0.1.1` werden
  ignoriert; gewertet wird ausschließlich `^v\d+\.\d+\.\d+$`.
- **Datei-Hygiene:** kein BOM, LF-Zeilenenden und bereits vorhandene
  Versions-Abschnitte am Dateiende bleiben unangetastet. Die Versions-Überschrift
  ist `## [vX.Y.Z](<repo>/releases/tag/vX.Y.Z) - JJJJ-MM-TT` (Datum = Commit-Datum
  des Tags). Umlaute bleiben echtes UTF-8 (Markdown-Regel, **kein** `\uXXXX`).

**Grenzen / was es NICHT tut:** Es ist ein rein **struktureller** Umbau. Es
repariert **keine** kaputt kodierten Umlaute (Mojibake wie `fÃ¼r`/`â€"` in alten
Einträgen) und ändert keine inhaltliche Rubriken-Fehleinordnung aus der
Vergangenheit — beides bleibt bewusst dem Menschen überlassen. Schreibe die Datei
nur mit `--write`, prüfe danach das `git diff`, und **committe/pushe nichts**, wenn
der Nutzer das nicht ausdrücklich verlangt.

## Schritt 7 — Fortschritt fortschreiben (Marker setzen)

Zum Schluss — **erst wenn die Release Notes fertig geschrieben und der Changelog
nachgeführt ist** — schreibe den lokalen Fortschrittsmarker auf den jetzt
abgedeckten Endpunkt fort, damit der nächste Lauf ohne Startpunkt nahtlos hier
weitermacht:

```bash
python .claude/skills/release-notes/scripts/track_release_notes.py --set
# --set ohne Wert = HEAD (Normalfall). Alternativ ein Tag/Commit/Datum:
#   ... --set v0.3.57        ... --set 2026-06-04
```

- **Immer ausführen, auch bei explizitem `--since`** — so bleibt der Marker aktuell,
  egal wie der Lauf gestartet wurde.
- **Der Marker geht nur vorwärts.** `--set` weigert sich, ihn zurückzudrehen — etwa
  nach einem bewussten Nachtrag für ein altes Fenster (`--since v0.3.40 --until
  v0.3.45`), während der Marker schon bei v0.3.57 steht; sonst tauchten beim
  nächsten Lauf bereits kommunizierte Änderungen erneut auf. Nur mit `--force`
  überschreiben, etwa nach einem absichtlichen History-Rewrite.
- **Gab es nichts Neues** (Schritt 1 meldete „nothing new"), ist kein `--set` nötig
  — der Marker bleibt, wo er ist.
- Die `.release-notes-state.json` **nie committen** — sie ist und bleibt lokal
  (`.gitignore` sorgt dafür, der Schritt prüft es zusätzlich).

## Best Practices für Release Notes (Leitlinien mit Begründung)

Diese Prinzipien stehen hinter den Schritten oben — verinnerliche das *Warum*, dann
triffst du auch in Grenzfällen die richtige Entscheidung:

1. **Schreibe für das Publikum, nicht für dich.** Die Leser sind Nutzer, keine
   Entwickler. Ihre Sprache ist die der App-Oberfläche. Jeder Begriff, den sie in
   der App nie sehen, ist ein Begriff zu viel.
2. **Nutzen vor Mechanik.** Menschen lesen Release Notes, um „Was bringt mir das?"
   zu beantworten. Die Implementierung ist die Antwort auf eine Frage, die sie nicht
   gestellt haben.
3. **Nur Wahrnehmbares.** Wenn niemand den Unterschied bemerkt, gehört er nicht in
   Nutzer-Notes. Interne Arbeit ist real und wichtig — aber sie ist Stoff für das
   Changelog/PR, nicht für die Nutzer-Ankündigung.
4. **Gruppieren und priorisieren.** Neu/Verbesserungen/Fehlerbehebungen plus
   „Wichtigstes zuerst" macht die Notes scanbar. Niemand liest eine flache Liste
   von 40 Punkten.
5. **Eine Idee pro Zeile, kurz und konkret.** Fett gesetztes Schlagwort + ein, zwei
   Sätze. Verwandte Commits zu einem Nutzerpunkt bündeln.
6. **Konsistenter Ton.** Durchgängig unpersönlich/neutral (keine direkte Anrede),
   Präsens, aktiv. Ton und Begriffe nicht mitten im Dokument wechseln.
7. **Bei Fehlerbehebungen positiv & ehrlich, aber ohne Obduktion.** „X funktioniert
   wieder" genügt; die technische Ursache bleibt draußen. Nichts schönreden, nichts
   verschweigen, aber auch keine Angst säen.
8. **Handlungsbedarf klar benennen.** Falls der Nutzer etwas tun muss (neu
   importieren, neu anmelden, Cache leeren) oder eine sichtbare Einschränkung
   bleibt (z. B. „Preise laufen bis zu 10 Min. nach"), sag es ausdrücklich und früh.
9. **Versions-Label und Datum** gehören in den Kopf — Notes ohne Bezugspunkt sind
   wertlos.
10. **Keine internen Referenzen.** Keine PR-/Issue-Nummern, Dateinamen,
    Migrations-IDs, API-Pfade oder Branch-Namen im Nutzertext.

## Schnell-Checkliste vor dem Abschluss

- [ ] Startpunkt korrekt aufgelöst (Datum vs. Tag vs. Commit), Zeitfenster stimmt.
- [ ] Nur nutzersichtbare Punkte; alle „Rendering unverändert"/intern-Einträge raus.
- [ ] Kein Jargon mehr: keine Pfade, `V###`, Framework-Namen, HTTP-Codes, PR-Nummern.
- [ ] Erste Zeile = Titel „Release Notes (TT.MM. → TT.MM.)" mit korrektem Fenster.
- [ ] Drei Rubriken, Wichtigstes zuerst, verwandte Punkte gebündelt.
- [ ] Einheitliches Format (verbindliche Stil- und Formatregeln): genau eine H1,
      Rubriken als H2 (keine H3+), Highlights als Fließtext-Absatz, jeder Punkt als
      `- **Schlagwort:** …`; keine Trennlinien, Tabellen, Inline-Code oder Emojis.
- [ ] Durchgängig unpersönlich (keine direkte Anrede „du"/„Sie").
- [ ] **Kein einziges Emoji** — Überschriften und Punkte rein als Text.
- [ ] Echte Umlaute (ä/ö/ü/ß, kein ue/oe/ae/ss), keine Mojibake aus den Quelldaten.
- [ ] Etwaiger Handlungsbedarf der Nutzer genannt.
- [ ] CHANGELOG nachgeführt (Schritt 6): Probelauf geprüft, dann `--write`; veröffentlichte
      `[Unreleased]`-Einträge stehen unter ihrem `## [vX.Y.Z]`-Abschnitt, `git diff` gesichtet.
- [ ] Fortschrittsmarker fortgeschrieben (Schritt 7): nach fertigen Notes
      `track_release_notes.py --set` ausgeführt; `.release-notes-state.json` ist git-ignored
      und wird nicht committet. (Bei „nothing new" entfällt das Setzen.)
