# E2E-Website-Tests: Umsetzungsplan (Playwright-Java + Testcontainers)

**Status:** Phase 0 abgeschlossen; Phase 1 (Fundament) im Kern fertig (`./gradlew :frontend:e2eTest` fährt den Stack selbst hoch/ab); Phase 2 (`data-testid`-Hooks) abgeschlossen; Phase 3 (funktionale Flows) begonnen — Flows „Mission anlegen" + „Job-Order anlegen" grün. Phasen 4–6 offen. Details unter „Phase 1/2/3 — Stand".
**Datum:** 2026-05-29.
**Scope:** automatisierte, browserbasierte Funktionstests der Frontend-Weboberfläche, lauffähig in der GitHub-CI gegen einen ephemeren Full-Stack und (später) gegen ein Staging-Deployment.

## Worum es geht

Heute gibt es **keine browserbasierten Tests**. Die gesamte Testsuite ist JVM-intern:

- **Backend:** JUnit 5, Mockito, MockMvc, `@SpringBootTest`, Testcontainers (nur Postgres), ArchUnit.
- **Frontend:** MockMvc-Controllertests, MockWebServer, ArchUnit, Security-/Config-Tests. Einige Tests prüfen gerendertes HTML — aber serverseitig, ohne echten Browser und ohne JavaScript-Ausführung.

Damit bleibt der echte End-to-End-Pfad (echter Browser, echter Keycloak-Login, echtes JavaScript, echte AJAX-/WebSocket-Interaktion, echte Session in Redis) ungetestet. Dieser Plan schließt diese Lücke.

## Leitentscheidungen (festgelegt)

1. **Treiber/Framework:** Playwright for Java (`com.microsoft.playwright:playwright`).
2. **Ziel-Umgebungen:** beide — ephemerer CI-Stack (PR-Gate) **und** Smoke gegen Staging.
3. **Orchestrierung:** Testcontainers in Gradle (`ComposeContainer`, wiederverwendet die vorhandenen Compose-Dateien).
4. **Staging:** existiert real noch nicht (nur der `:edge`-Image-Tag). Die Smoke-Harness wird gebaut, aber bis ein Staging-Host läuft in der CI **inaktiv** geschaltet.
5. **Erster Funktions-Scope:** Login -> Mission anlegen -> Hangar -> Refinery-Order -> JobOrder-Handover.

## Phase 0 — Ergebnisse (abgeschlossen, verifiziert 2026-05-29)

Der Spike lief erfolgreich gegen den vollen, lokal hochgefahrenen Stack: ein echter headless-Chromium (Playwright 1.60.0) durchläuft den Keycloak-OIDC-Login und landet authentifiziert im Frontend. Verifiziert aus **frisch importierten, eingecheckten Artefakten** (nicht handgepatcht):

```
[E2E] login OK in ~3.4 s | landing=https://localhost:18081/ | SESSION cookie=true | storageState geschrieben
```

**Gemessenes Zeitbudget** (Windows/Docker Desktop):
- App-Images bauen (beide Module, warmer Layer-Cache): ~122 s
- Full-Stack healthy (frische DB, Realm-Import, Flyway, alle Healthchecks): ~50 s
- Playwright-Login inkl. Browser-Start: ~3–6 s

**Entscheidung A vs. B: Variante A** (Images aus Quellcode bauen + kompletten Stack hochfahren). Der Image-Build kostet mit Layer-Cache nur ~2 min, und A ist prod-treu und nutzt die vorhandenen Compose-Dateien wieder. Variante B (deps-only + bootJar) bringt keinen ausreichenden Zeitvorteil, um die Mehrverdrahtung (zwei App-Prozesse starten/koordinieren) zu rechtfertigen.

**Orchestrierung — wichtige Korrektur:** Testcontainers `ComposeContainer` kann diesen Stack **nicht sauber** fahren — `--profile`, `--env-file`, mehrere `-f`-Dateien, feste Ports und der `!override`-Tag lassen sich darüber nicht zusammen ausdrücken. Der Spike fährt den Stack über die **`docker compose`-CLI** (exakt der dokumentierte Flow). **Phase-1-Empfehlung:** eine dünne, Gradle-/JUnit-gesteuerte Extension, die `docker compose … up/down` aufruft — nicht das wörtliche `ComposeContainer`. (Verfeinert Leitentscheidung 3.)

**Neu im Repo (Spike-Artefakte, reviewbar):**
- `frontend/src/e2e/java/.../LoginSmokeE2eTest.java` — Playwright-Login-Test (Screenshot/HTML-Dump bei Fehler).
- `frontend/src/e2e/resources/realm-export.e2e.json` — handgeschriebener synthetischer Realm (nur Wegwerf-Werte).
- `frontend/build.gradle.kts` — `e2e`-Source-Set + `:frontend:e2eTest` + `playwrightInstall` (nicht in `check`; `checkstyleE2e` deaktiviert; `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`).
- `gradle/libs.versions.toml` — Playwright `1.60.0`.
- `docker-compose.e2e.yml` — Isolations-Override (Named Volumes, Root-User-Workaround, KC-HTTP-Hostname).
- Lokal/gitignored: `.env.test`, `keystore.p12`, `realm-export.json` (Wegwerf).

**Befunde, die die Umsetzung beeinflusst haben (für Phase 1 / das Team):**
1. **Frontend serviert im dev/test-Profil HTTPS auf 18081** (`server.ssl.enabled=true`, self-signed), entgegen der README-Angabe „http://localhost:18081". E2E muss `https://` + `ignoreHTTPSErrors` nutzen und der Realm-Client die **https**-Redirect-URI erlauben. → README-Korrektur fällig.
2. **Die App-Images backen eine root-owned `/app/logs/backend.log`**: der AppCDS-Trainingslauf im Dockerfile läuft als root (vor `USER 10001`), und der Logback-FILE-Appender ist in **jedem** Profil aktiv (`<springProfile name="!prod">`). Wird `/app/logs` durch eine isolierte Mount-Strategie ersetzt (statt des prod-Bind-Mounts), kann der non-root-Runtime-User die Datei nicht schreiben → Boot-Abbruch. Spike-Workaround: Container als root. **Echter Fix gehört ins Dockerfile** (`/app/logs` nach dem CDS-Lauf chownen oder den Trainingslauf woanders loggen lassen) — betrifft auch jeden sauberen CI-Runner.
3. **`host.docker.internal` löst auf dem Host nicht zwingend auf die Loopback-Adresse auf** (hier: 10.1.0.30), während Keycloak nur auf `127.0.0.1:18080` published ist → der Host-Browser erreicht den Issuer-Host nicht. Spike-Fix: Chromium `--host-resolver-rules=MAP host.docker.internal 127.0.0.1`. **Für CI braucht es eine bewusste Issuer-Hostname-Strategie** (Browser im selben Docker-Netz, oder KC auf allen Interfaces publishen).
4. **Backend-Readiness hängt nicht an der Keycloak-Admin-API**, nur am OIDC-Discovery-Dokument — der synthetische Realm muss den `backend-service`-Client für den Start also nicht perfekt treffen.
5. Playwright lädt bei `create()` per Default alle drei Browser; mit `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` + `playwrightInstall chromium` wird nur Chromium geladen.
6. **`cyclonedxBom` zieht die `e2e`-Source-Set-Dependencies in das Frontend-SBOM** (Playwright tauchte im `frontend-bom.json` auf, obwohl es nie ins Image gelangt). Phase 1 muss den BOM-Task auf die Produktiv-Configs scopen (`skipConfigs`/`includeConfigs`), sonst verschmutzt jeder CI-SBOM-Lauf die Stückliste mit Test-Abhängigkeiten.

**Noch offen (Phase 1+):** `docker compose`-CLI-Orchestrierung als JUnit-Extension formalisieren, Testdaten-Seeding, `data-testid`-Vorarbeit, funktionale Flows, CI-Workflow.

## Phase 1 — Stand (2026-05-29)

**Im Kern fertig: `./gradlew :frontend:e2eTest` ist jetzt selbstständig.** Aus einem leeren Zustand baut es die Images, fährt den vollen Stack hoch, loggt sich ein und räumt mit `--volumes` wieder ab — kein manuelles `docker compose` mehr. Verifiziert: BUILD SUCCESSFUL in ~3,5 min (inkl. Image-Build), `[E2E] login OK … SESSION cookie=true`, danach keine Container/Volumes übrig.

**Neu/geändert:**
- `frontend/src/e2e/java/.../E2eStackExtension.java` — JUnit-5-Extension, die `docker compose up/down` treibt (statt `ComposeContainer`): staffelt den Realm aus dem Classpath nach `./realm-export.json`, generiert den Wegwerf-Keystore via `keytool` aus `java.home`, setzt die Wegwerf-Credentials direkt als Subprozess-Env (kein `.env`-File auf Platte), `up -d --build --wait`, Teardown genau einmal am Ende des Testplans über eine Root-Store-`CloseableResource`. **Ziel-agnostisch:** ist `E2E_BASE_URL` gesetzt, verwaltet die Extension kein Docker (Staging-Modus).
- `LoginSmokeE2eTest` registriert die Extension via `@RegisterExtension`; der `host.docker.internal`-Remap greift nur im lokalen Modus.
- `.dockerignore` schließt `**/src/e2e` aus → E2E-Edits busten den Backend/Frontend-Image-Layer-Cache nicht mehr.
- `:frontend:e2eTest` setzt die `e2e.*`-System-Properties nur noch bei explizitem `-P`, sonst übernimmt die Extension den ephemeren Modus.

**Nachträglich erledigt (2026-05-29):**
- **SBOM-Scoping (Befund 6) behoben:** `skipConfigs`/`includeConfigs` sitzen in cyclonedx-gradle 3.x auf dem **`cyclonedxDirectBom`**-Task (`CyclonedxDirectTask`, der den Dependency-Scan macht), nicht auf dem Aggregat-Task `cyclonedxBom`. `skipConfigs.set(listOf("^e2e.*"))` dort → Playwright nicht mehr im `frontend-bom.*` (verifiziert: 0 Treffer).
- **Playwright-Tracing ergänzt:** der Login-Test zeichnet jetzt einen Trace (Screenshots + DOM-Snapshots + Sources) auf und speichert ihn nach `build/e2e/trace.zip` (~0,8 MB; mit `npx playwright show-trace` inspizierbar); die Fehler-Diagnostik fängt zusätzlich Assertion-Fehler, nicht nur Playwright-Timeouts.

**Noch offen in Phase 1 (an spätere Phasen gekoppelt):**
- **Login-Helper + `storageState`-Wiederverwendung** über mehrere Tests — sinnvoll erst mit mehreren Flows (Phase 3).
- **Testdaten-Seeding** der Stammdaten per Admin-API — erst von den Phase-3-Flows benötigt.
- **CI-Workflow** — Phase 5.

## Phase 2 — Stand (2026-05-29, abgeschlossen)

21 `data-testid`-Hooks in 8 Templates eingezogen — nur die von den vier Flows + Navigation berührten Elemente (Diff: 21+/21−, keine BOM-/LF-Änderung; Frontend-Render-Tests grün).

**Konvention:** kebab-case, flow-präfigiert; ein test-eigenes Attribut, entkoppelt von i18n-Text (`th:text`), CSS-Klassen und JS-Hooks (`data-trigger`). Der Login läuft über die Keycloak-Seite (`#username` / `#password` / `#kc-login`) — dort sind keine Testids möglich. Wo bereits stabile `id`s existieren (`#ship-type`, `#ship-name`, `#ship-insurance`, `#ship-location`, `#ownerId`, `#locationId`, `#recipientHandle`, `#add-handover-item-btn`, `form="mission-form"`), nutzt Phase 3 diese direkt.

**Inventar:**
- Navigation (`fragments/sidebar.html`): `nav-missions`, `nav-orders`, `nav-refinery`, `nav-hangar`.
- Mission anlegen: `missions-create-link`, `mission-row` (`missions.html`); `mission-form`, `mission-name-input`, `mission-status-select`, `mission-start-date`, `mission-start-time` (`mission-detail.html`).
- Hangar: `hangar-add-ship`, `hangar-ship-form`, `hangar-ship-submit`, `hangar-ship-row` (`hangar.html`).
- Refinery-Order: `refinery-create-link` (`refinery-orders-index.html`); `refinery-form`, `refinery-add-material`, `refinery-submit` (`refinery-orders-create.html`).
- JobOrder: `order-row` (`orders-index.html`); `order-material-select`, `order-material-amount`, `order-submit` (`orders-create.html`); `order-handover-open`, `order-handover-form`, `order-handover-submit` (`orders-detail.html`).

**Noch offen:** Die `data-testid`-Vergabe ist auf die fünf Phase-1/3-Flows begrenzt; weitere Flows bekommen ihre Hooks, wenn sie getestet werden.

## Phase 3 — Stand (2026-05-29, begonnen)

**Zwei funktionale Flows grün: „Mission anlegen" + „Job-Order anlegen".** `./gradlew :frontend:e2eTest` führt drei Tests aus (Login-Smoke + Mission-Create + JobOrder-Create), alle grün gegen den selbst-hochgefahrenen Stack (zweimal in Folge verifiziert, sauberer Teardown).

**Neu:**
- `BackendSeeder` — seedet die Create-Voraussetzungen: (1) `ensureIridiumMembership` holt per Keycloak-Password-Grant ein `test-admin`-Token und weist den User via `PATCH /api/v1/users/{id}/squadron` der IRIDIUM-Staffel zu (mit 409-Retry gegen den `syncUser`-Versions-Bump), damit staffel-scoped Creates nicht 400en; (2) `ensureJobOrderMaterial` legt per `POST /api/v1/materials` ein `isJobOrder=true`-Material an (`categoryId` optional). Trust-all-SSL für das self-signed Backend-Zertifikat. Greift nur im ephemeren Modus.
- `E2eSupport` — geteilte Helfer: Chromium-Launch (mit `host.docker.internal`-Remap im lokalen Modus), Keycloak-Login, `storageState`-Capture und Failure-Dump (Screenshot + HTML).
- `MissionCreateE2eTest`, `JobOrderCreateE2eTest` — je: navigieren → Create-Formular füllen → Submit → Ergebnis in der Liste verifizieren (auto-waiting).

**Befunde:**
- Das Mission-Create-Formular blockiert den Submit **clientseitig** (HTML-`required` auf Planned-Start-Datum/Zeit) ohne sichtbare Server-Fehlermeldung — Flow-Tests müssen die clientseitig-`required`-Felder füllen (mit Zukunfts-Datum, sonst greift die Not-in-the-past-Prüfung).
- **Race vermeiden:** `datetime-splitter.js` leert die Datum/Zeit-Picker beim `DOMContentLoaded` aus dem leeren Hidden-Feld. Wer vor diesem Init füllt, dessen Werte werden wieder gelöscht → der Submit wird stumm blockiert (intermittierender Fehlschlag). Erst auf vollständigen Page-Load warten, dann füllen.
- **Test-Isolation:** `storageState` wird NICHT über Testklassen hinweg memoisiert — jede Klasse loggt frisch ein (eigene Session). Cross-Class-Session-Sharing hatte sonst nicht-deterministische Fehlschläge verursacht.

**Noch offen — gemeinsamer Blocker (UEX-Katalog / verknüpftes Inventar):**
- **Refinery-Order** — Flow + Seeding-Scaffold (`BackendSeeder.createLocation` / `createRefiningMethod` / `createRefineryMaterial` / `seedEntity`) sind vorhanden, der Test ist aber `@Disabled`: das Standort-Dropdown listet nur **refinery-hosting** Standorte (`getRefineryLocations` → `GET /api/v1/locations/refineries`, UEX-synced); ein einfaches `POST /api/v1/locations` erzeugt keinen Raffinerie-Standort. (Befund: `LocationDto` hat ein primitives `boolean hidden`, das beim POST mitgesendet werden muss.)
- **Hangar** — blockiert: ShipTypes sind UEX-synced (kein POST-Endpunkt), praktisch DB-Insert-only.
- **JobOrder-Handover** — technisch machbar, aber vertagt: das Übergabe-Item-Dropdown listet das an den Auftrag **verknüpfte Inventar** (`name="items[N].inventoryItemId"`), nicht bloße Materialien — braucht also zusätzlich Inventar-Seeding plus die Modal-Interaktion (Datum/Zeit + Empfänger + Item).

**Gemeinsames Thema:** Flows jenseits von Mission/Job-Order hängen an UEX-Katalogdaten (ShipTypes, Raffinerie-Standorte) oder verknüpftem Inventar, die eine frische DB nicht hat und die einfachen Admin-POSTs nicht liefern. Vor weiterem Ausbau zu entscheiden: (a) einen UEX-Katalog-Snapshot vor dem Stack-Up in die Test-DB seeden, (b) direktes JDBC-DB-Seeding (ShipType / refinery-fähige Location / Inventar), oder (c) den UEX-Sync auf Service-Ebene mocken.

Login-Helper/`storageState` (offener Phase-1-Punkt) ist erledigt.

## Warum dieser Stack zu diesem Projekt passt

- **Reines Java-25/Gradle-Ökosystem.** Playwright-Java und Testcontainers bleiben im selben Build, derselben Sprache und derselben CI-Toolchain. Kein zweites Ökosystem (Node/npm), das gegen die Projektregel *"Gradle ist der einzige sanktionierte Testpfad"* läuft.
- **Testcontainers ist bereits etabliert** (Backend nutzt `testcontainers-junit` + `testcontainers-postgresql`). `ComposeContainer` steckt im Testcontainers-Core und ist transitiv schon auf dem Classpath — keine neue Orchestrierungs-Abhängigkeit nötig.
- **Auto-Waiting von Playwright** entschärft genau die asynchronen Stellen, die hier sonst flaky wären: AJAX-Dropdowns mit `data-version`-Sync, WebSocket-Presence, Optimistic-Locking-409 unter Timing-Druck.
- **Vorhandener, dokumentierter Test-Stack** (`docker-compose.test.yml` + `.env.test` + synthetischer Realm mit Wegwerf-Credentials) liefert die Blaupause für den ephemeren Stack.

## Zielarchitektur der Teststufen

Eine einzige, **ziel-agnostische** Playwright-Suite, gesteuert über eine `E2E_BASE_URL`-Abstraktion, getrennt durch zwei JUnit-Tags:

| Tag | Ziel | Inhalt | CI-Lauf |
|---|---|---|---|
| `@Tag("e2e")` | ephemerer Stack (ComposeContainer) | volle funktionale Flows inkl. **destruktivem CRUD** | PR-Gate (Label- oder Nightly-getriggert) |
| `@Tag("smoke")` | beliebige `E2E_BASE_URL` (Staging) | nur Login + Kernseiten laden, **nicht-destruktiv** | nach Release gegen `:edge` — **vorerst deaktiviert** |

Dieselben Page-Objects laufen gegen beide Ziele; nur Datenanlage und Teardown unterscheiden sich.

## Login-Mechanik (zentral)

Die Frontend-Session ist **Cookie-basiert** (Spring Session/Redis, etabliert über den OIDC-Authorization-Code-Flow) — es liegt **kein Bearer-Token** im Browser. Daraus folgt:

- Der Test muss **einmal** ein echtes Keycloak-Login durch die UI durchspielen. Die Keycloak-Default-Loginseite hat stabile Feld-IDs: `#username`, `#password`, Submit `#kc-login`.
- Danach wird der authentifizierte Zustand per Playwright-`storageState` (Cookies) gesnapshottet und pro Rolle wiederverwendet. Das reduziert Keycloak-Last und Laufzeit drastisch.
- Token-Injection (Direct-Access-Grant) bringt für die **Browser**-Session nichts, weil die Session nicht token-, sondern cookie-getragen ist. Sie wäre nur für direkte Backend-API-Aufrufe nützlich (hier nicht der Pfad).

## Phasenplan

### Phase 0 — Spike / Risikoabbau (zuerst, als Checkpoint)

Beweist die heikelste Integration end-to-end, bevor in die Suite investiert wird:

- `ComposeContainer` über `docker-compose.yml` + `docker-compose.test.yml` hochfahren; App-Images aus dem aktuellen Stand via `docker-compose.build.yml` bauen.
- Synthetischen Realm laden; ein Playwright-Test: Keycloak-Login durchspielen -> authentifiziert auf dem Dashboard landen -> `storageState` snapshotten.
- **Misst das Zeitbudget** (Image-Build + Keycloak-Boot). Das Ergebnis entscheidet die einzige offene Sub-Frage (siehe unten): ComposeContainer-mit-App-Images vs. Deps-only-Testcontainers + die zwei Spring-Boot-Apps aus `bootJar` starten.
- **Aufwand:** ~0,5-1 Tag. **Risiko:** hoch (deshalb zuerst).

### Phase 1 — Fundament (Gradle + Stack-Bootstrap)

- **Dependency** in `gradle/libs.versions.toml`: `playwright` als `[versions]`- und `[libraries]`-Eintrag (analog zu `testcontainers-junit`). `ComposeContainer` braucht keine neue Dependency.
- **Eigenes Source-Set** `frontend/src/e2e/java` + Gradle-Task `:frontend:e2eTest` (Typ `Test`, `useJUnitPlatform()`), **bewusst nicht** in `check`/`test`/`build` verdrahtet, damit die schnelle Haupt-Pipeline unberührt bleibt. Aufruf explizit: `./gradlew :frontend:e2eTest`.
- **Playwright-Browser-Install-Task** (Chromium), in der CI über `~/.cache/ms-playwright` gecacht.
- **Synthetischer Realm** `frontend/src/e2e/resources/realm-export.e2e.json` — **von Hand erstellt, niemals aus der Produktion abgeleitet**, eigener Dateiname (kollidiert nicht mit der gitignorierten `realm-export.json`): Realm `iri`, Public-Client `basetool-frontend`, Confidential-Client `backend-service` (Wegwerf-Secret), Test-User je benötigter Rolle (Admin/Officer/Logistician/...).
- **Stack-Fixture** (JUnit-Extension): ComposeContainer, Health-Gating auf `/actuator/health` plus Keycloak-/Redis-Healthchecks, Wegwerf-`keystore.p12` zur Laufzeit per `keytool` (Recipe steht im README, Abschnitt 4.4), Wegwerf-Credentials als Test-Properties.
- **Harness:** `E2E_BASE_URL`-Abstraktion, Playwright-Fixtures, Login-Helper + `storageState`, **Trace/Screenshot/Video bei Fehlschlag** (Artefakt-Upload analog zu den bestehenden Report-Uploads), Testdaten-Seeding der nötigen Stammdaten (Materials/ShipTypes/Locations/Cities) **per Backend-API mit Admin-Token** im `@BeforeAll`. Kein Flyway-Test-Seed, da Migrationen auch in Prod laufen.
- **Aufwand:** ~2-3 Tage. **Risiko:** mittel.

### Phase 2 — `data-testid`-Vorarbeit (nur Phase-1-Flows)

- Stabile `data-testid`-Hooks in die Thymeleaf-Templates der fünf Flows einziehen. **Keine** text-/i18n-abhängigen Selektoren (sonst de/en-Bruch). Scope strikt auf die berührten Elemente begrenzt. Kein neuer sichtbarer Text -> keine `messages.properties`-Änderung.
- **Aufwand:** ~1 Tag. **Risiko:** niedrig.

### Phase 3 — Funktionale Suite (ephemeral, `@Tag("e2e")`)

- Page-Objects + Tests für: **Login** (storageState-Setup) -> **Mission anlegen** -> **Hangar** -> **Refinery-Order** -> **JobOrder-Handover**. Letzterer ist der Concurrency-/409-sensible Flow und bewusst als E2E-Kandidat gewählt. Playwrights Auto-Waiting deckt die AJAX-/`data-version`-/409-Timing-Themen ab.
- **Aufwand:** ~3-5 Tage. **Risiko:** mittel (Flakiness/Selektoren).

### Phase 4 — Smoke-Subset (`@Tag("smoke")`, ziel-agnostisch)

- Login + Laden der Kernseiten, **nicht-destruktiv**, parametrisiert über `E2E_BASE_URL` + Credentials aus CI-Secrets. Läuft sowohl gegen den ephemeren Stack als auch (später) gegen Staging.
- **Aufwand:** ~0,5-1 Tag. **Risiko:** niedrig.

### Phase 5 — CI-Integration

Neuer Workflow `.github/workflows/e2e.yml`:

- **PR-Gate-Job** (ephemerer Stack, `@Tag("e2e")`): getriggert per `e2e`-Label und/oder nightly, um CI-Minuten zu schonen (Full-Stack ~2 GB RAM + Keycloak-Boot). Docker-Layer-Cache (gha) + Playwright-Binary-Cache. Trace-Artefakte mit `if: always()`.
- **Staging-Smoke-Job** (`@Tag("smoke")`): **authored, aber `if:`-gegated auf ein gesetztes `E2E_BASE_URL`-Secret** -> läuft erst, wenn ein Staging-Host existiert. Kein toter Pflicht-Check.
- **Aufwand:** ~1-2 Tage. **Risiko:** mittel (CI-Stabilität/Timeouts).

### Phase 6 — Doku

- README-Abschnitt "E2E-Tests" (lokal ausführen, CI-Verhalten, Staging-Voraussetzung). CHANGELOG-Eintrag (knapp). Javadoc + `spotlessApply` auf allen neuen Java-Dateien.
- **Aufwand:** ~0,5 Tag.

## Offene Sub-Entscheidung (durch Phase 0 zu klären)

**Wie wird das System under Test hochgefahren?**

- **Variante A — ComposeContainer mit App-Images:** baut Backend- und Frontend-Image aus dem aktuellen Stand und fährt den kompletten Stack über die vorhandenen Compose-Dateien hoch. Maximal prod-treu, aber schwerer (Image-Build-Zeit pro Lauf).
- **Variante B — Deps-only-Testcontainers + bootJar:** Testcontainers nur für Postgres x2, Keycloak, Redis; Backend und Frontend laufen aus den von Gradle gebauten `bootJar`s, auf die Container gezeigt. Leichter und schneller, aber weniger prod-treu und mehr Verdrahtung zum Starten der zwei App-Prozesse.

Der Phase-0-Spike misst das Zeitbudget und liefert die Entscheidungsgrundlage.

> **Entschieden (Phase 0): Variante A.** Image-Build mit Cache ~2 min, prod-treu, Wiederverwendung der vorhandenen Compose-Dateien. Details unter „Phase 0 — Ergebnisse".

## Explizit separat / aktuell geblockt: realer Staging-Host

Da Staging heute nur ein Image-Tag ist, kann die Smoke-Stufe noch nicht *laufen*. Den Host aufzubauen ist ein **eigenes Work-Item** (nicht Teil dieses Plans). Grobe Optionen zur späteren Entscheidung:

- **Option A:** kleiner Dauer-Host zieht `:edge` (analog zum Prod-Compose hinter nginx-proxy-manager) + eigener Staging-Keycloak mit synthetischem Test-User.
- **Option B:** ephemeres Per-Release-Deploy (hochfahren -> smoke -> abreißen).

Voraussetzung in beiden Fällen: ein Test-User im **Staging**-Keycloak plus `E2E_BASE_URL`/Credentials als CI-Secrets. Bis dahin liefert die Suite ihren Wert gegen den ephemeren Stack.

## Querschnittsthemen

- **Secrets:** ausschließlich Wegwerf-Werte; synthetischer Realm von Hand, nie aus Prod abgeleitet; Stack-Teardown mit `down --volumes`. Deckt sich mit der `.env.test`-Isolationsregel aus CLAUDE.md.
- **Gradle-only:** Einstieg bleibt `./gradlew :frontend:e2eTest` -> erfüllt die Projektregel auch für E2E.
- **Pipeline-Schutz:** E2E strikt getrennt von `build`/`check`; die bestehende CI wird nicht verlangsamt.
- **i18n-Robustheit:** Selektoren ausschließlich über `data-testid`, niemals über sichtbaren (lokalisierten) Text.

## Risiken (konsolidiert)

| Risiko | Schwere | Gegenmaßnahme |
|---|---|---|
| Flakiness (async, 409, Keycloak-Timing) | hoch | Auto-Waiting (Playwright), Health-Gating, Test-Retries, `storageState` |
| Secrets-Leak (Prod-Realm/Keystore in CI) | hoch | eigener synthetischer Realm, Runtime-Keystore, Wegwerf-Credentials, Teardown `--volumes` |
| Brüchige Selektoren durch i18n | mittel-hoch | `data-testid` in Templates (Vorarbeit), keine Text-Selektoren |
| CI-Minuten/RAM (Full-Stack) | mittel | eigener Job, gestaffelte Trigger, Caching |
| Wartungslast (Page-Objects) | mittel | klein anfangen, Page-Object-Pattern, nur kritische Flows |
| Staging existiert real nicht | offen | Smoke-Job gegated; Host-Aufbau als separates Work-Item |

## Nächster Schritt

Start mit **Phase 0 (Spike)** als Checkpoint. Danach folgt ein Bericht über Zeitbudget und die Variante-A-vs-B-Entscheidung, bevor die Suite ausgebaut wird. Vor diesem Go wird kein Code angefasst.
