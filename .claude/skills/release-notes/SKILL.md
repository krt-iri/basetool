---
name: release-notes
description: Use this skill to write user-facing release notes / "Was ist neu" / Änderungsübersicht / Patch Notes / Update-Ankündigung for the Profit Basetool app, starting from a given point in time (a date, a git tag like v0.3.40, a release, or a commit). It turns the technical German CHANGELOG.md and git history into friendly, non-technical release notes for the squadron's normal members, grouped into Neu / Verbesserungen / Fehlerbehebungen. Trigger this whenever the user wants to communicate recent changes to end users — e.g. "schreib Release Notes seit v0.3.40", "was ist neu seit dem 15.05 für die Nutzer", "fasse die letzten Änderungen für die Mitglieder zusammen", "Update-Ankündigung für die letzten zwei Wochen" — even when they don't say the words "release notes".
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

Fehlt der Startpunkt komplett, frag genau einmal kurz nach („Ab welchem Datum oder
welcher Version?") und schlage das letzte Tag als Default vor — rate nicht.

## Schritt 1 — Rohmaterial sammeln

Führe das Hilfsskript vom Repo-Wurzelverzeichnis aus. Es legt beide Quellen
nebeneinander: den `[Unreleased]`-Block der CHANGELOG.md (die reichhaltigsten
Beschreibungen) und das nach Commit-Typ sortierte Git-Log (das das Zeitfenster
sauber abgrenzt).

```bash
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
beschreibt jede Änderung ausführlich und benutzernah, trägt aber **kein Datum pro
Eintrag**. In diesem Projekt liegt zudem praktisch die **gesamte Historie** unter
`## [Unreleased]` (die Versions-Tags wurden nie als eigene Changelog-Abschnitte
geschnitten) — der Block ist also riesig, nach Rubrik gruppiert, mit den
**neuesten Einträgen jeweils oben**. Verlass dich für den **Umfang** deshalb strikt
auf das **Git-Log** (es grenzt das Fenster exakt ab und signalisiert über die
Commit-Typen `feat`/`fix`/`perf` vs. `chore`/`refactor`/`test`/`docs`, was
überhaupt nutzerrelevant ist) und greife im Changelog nur die **oberen** Einträge
jeder Rubrik ab, die zu den Commits deines Fensters passen. **Kopiere nie den
ganzen `[Unreleased]`-Block** — das meiste davon liegt weit vor deinem Startpunkt.

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
  kurzer **Highlights**-Block (1–3 Sätze) ganz oben für die größten Neuerungen.
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

```markdown
# Release Notes (TT.MM. → TT.MM.)

_Version <Label> · Stand <TT.MM.JJJJ>_   <!-- optionale Unterzeile, nur falls ein Versions-Label vorliegt -->

## Highlights
- <1–3 Sätze zu den größten Neuerungen — nur bei längeren Notes>

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
- [ ] Durchgängig unpersönlich (keine direkte Anrede „du"/„Sie").
- [ ] **Kein einziges Emoji** — Überschriften und Punkte rein als Text.
- [ ] Echte Umlaute (ä/ö/ü/ß, kein ue/oe/ae/ss), keine Mojibake aus den Quelldaten.
- [ ] Etwaiger Handlungsbedarf der Nutzer genannt.
