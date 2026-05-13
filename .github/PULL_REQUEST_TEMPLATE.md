<!--
Danke für den PR. Bitte fülle die unten markierten Pflichtbereiche aus
und arbeite die Checkliste so weit ab, wie sie zutrifft. Sektionen mit
"(falls betroffen)" sind optional — der Reviewer prüft die Naht zum Code.

Falls dies ein Draft-PR ist: markiere ihn als Draft und kennzeichne den
Titel mit "WIP".
-->

## Worum geht's?

<!--
1-3 Sätze: Was ändert dieser PR und WARUM (nicht WAS — das zeigt der Diff).
Verlinke verwandte Issues (z. B. `closes #123`, `refs #456`).
-->

## Art der Änderung

- [ ] Bug Fix (nicht-breaking, behebt ein konkretes Problem)
- [ ] Feature (nicht-breaking, fügt Funktionalität hinzu)
- [ ] Breaking Change (bestehende API/Verhalten ändert sich — neue API-Version oder Migration nötig)
- [ ] Refactor / Tech-Debt (keine user-sichtbare Änderung)
- [ ] Dokumentation
- [ ] Build / CI / Dependencies
- [ ] Test-Coverage / Test-Infrastruktur

## Betroffene Module

- [ ] `backend`
- [ ] `frontend`
- [ ] Beide
- [ ] Sonstiges (Build / Compose / Docs / Workflows)

## Wie wurde getestet?

<!--
Konkret: Was wurde verifiziert? Welche Profile, welche Stack-Variante
(Gradle bootRun, dev-Compose, Test-Stack mit `.env.test`)? Bei UI-Änderungen:
welche Geräteklassen (Smartphone / Tablet / Desktop / Ultra-wide) und
welche Browser? Falls UI-Tests nicht möglich waren, das EXPLIZIT sagen —
nicht "Tests passen" als Stellvertreter benutzen.
-->

- 

## Checkliste

### Allgemein

- [ ] PR-Titel folgt Conventional Commits (`feat(...)`, `fix(...)`, `chore(...)`, `docs(...)`, `refactor(...)`, `test(...)`).
- [ ] Branch ist aktuell mit `main` (rebased oder gemergt, kein Stale-Diff).
- [ ] `CHANGELOG.md` wurde unter `## [Unreleased]` aktualisiert (passende Kategorie: Added / Changed / Fixed / Removed / Security).
- [ ] Verwandte Issues sind verlinkt (`closes #...`, `refs #...`).
- [ ] Keine echten Secrets, Tokens, Passwörter, Keystores oder personenbezogenen Daten im Diff.

### Code-Qualität

- [ ] `./gradlew check` läuft lokal grün (Checkstyle, SpotBugs, Tests).
- [ ] Alle Checkstyle- und SpotBugs-Findings im **geänderten Code** sind behoben — keine neuen Warnungen on top.
- [ ] Keine `@SuppressWarnings` / `@SuppressFBWarnings` / Checkstyle-Suppressions ohne One-Line-Kommentar, der die Begründung für dieses spezifische Call-Site nennt.
- [ ] Konstruktor-Injection über Lombok `@RequiredArgsConstructor`, kein Field-`@Autowired`.
- [ ] Logger ausschließlich via `@Slf4j`, nicht manuell instanziiert.
- [ ] Records für DTOs und immutable Config-Wrapper, kein POJO-Boilerplate.
- [ ] Javadoc auf jeder neuen/geänderten Klasse, jedem Interface, Enum, Record und public/protected Methode — konkret und code-spezifisch, kein generisches "Returns the value".

### Tests

- [ ] Neue Features / Fixes haben Tests (Naming: `*Test`, Given/When/Then-Struktur).
- [ ] Tests laufen **ausschließlich** über `./gradlew test` — kein IDE-Test-Runner.
- [ ] Bei Concurrency-/Locking-Änderungen: Optimistic-Lock-Pfade getestet, `*WithinTransaction`-Pattern respektiert (siehe CLAUDE.md > Concurrency).
- [ ] Keine produktiven Credentials in Tests oder in lokal hochgezogenen Test-Stacks (`.env.test` + Throwaway-Keystore + gestripptes Realm verwenden).

### API & Datenbank (falls betroffen)

- [ ] Neue REST-Endpoints unter `/api/v1/...`; Breaking Changes erzeugen `/api/v2/...` plus `@ApiDeprecation(sunset=..., replacement=...)` auf dem alten Endpoint.
- [ ] Jeder neue/geänderte `@RestController`-Endpoint trägt `@PreAuthorize` und vollständige SpringDoc-Annotationen (`@Operation` mit Summary/Description, `@ApiResponses` mit fachlichen Beschreibungen pro Status-Code).
- [ ] DTOs (Records) an der Controller-Boundary, MapStruct-Mapper für Entity<->DTO — **keine JPA-Entities** im Response.
- [ ] `@Valid` auf jedem `@RequestBody` (POST/PUT/PATCH); Write-DTOs tragen Jakarta-Validation-Annotationen.
- [ ] Listen-Endpoints akzeptieren `Pageable` und liefern `PageResponse`; Sort-Felder sind in einer **Whitelist** im Service beschränkt (kein User-Input direkt in `Sort`).
- [ ] Zeitstempel als `Instant` / `OffsetDateTime` in UTC; Timezone-Umrechnung passiert ausschließlich in der Display-Schicht.
- [ ] `backend/src/main/resources/api/openapi.json` ist synchron zu den Controller-Änderungen.
- [ ] Neue DB-Änderungen liegen als `V<n>__<desc>.sql`-Flyway-Migration vor; `ddl-auto` bleibt `validate`.
- [ ] Destruktive DB-Operationen folgen dem Two-Phase-Pattern aus `backend/src/main/resources/db/migration/README.md`.

### Security & Datenisolation (falls betroffen)

- [ ] Lese-/Schreib-Operationen filtern im Service-Layer nach JWT-`sub`, außer der Caller hat eine erhöhte Rolle (`ADMIN`, `OFFICER`, ...).
- [ ] Für Guests: sensible Felder werden explizit in der Controller-Schicht entfernt (`cleanupForGuest`-Style).
- [ ] Kein direkter `SecurityContextHolder`-Zugriff außerhalb des Auth-Helper-Service (wird per ArchUnit erzwungen).
- [ ] Frontend hängt nicht an Spring Data JPA und greift nicht direkt auf DB oder Keycloak Admin API zu (ArchUnit-Regel).
- [ ] Keine Tokens, Mails oder Klarnamen in Logs.

### UI / Frontend (falls betroffen)

- [ ] Layout funktioniert auf Smartphone (<=768px), Tablet (768-1024px), Desktop (1024-1600px) und Ultra-wide (1600px+); Touch-Targets >= 44px.
- [ ] Styleguide eingehalten (Brand-Orange `#E77E23`, `Ethnocentric`/`Lato`-Fonts, Departement-Farben semantisch korrekt).
- [ ] Keine `confirm()` / `alert()` / nativen Browser-Dialoge — stattdessen KRT-Modals/Toasts.
- [ ] Jede user-sichtbare Zeichenkette kommt aus `messages.properties` (de + en + Fallback); Umlaute in `.properties` als `\uXXXX`, in Markdown literal UTF-8.
- [ ] DOM-`data-version`-Attribute werden nach AJAX-Updates konsistent in **alle** verwandten Elemente propagiert (Edit-Buttons, Modals, Action-Buttons in derselben `<tr>`/Container) — sonst 409 beim nächsten Klick.
- [ ] Resilience4j-Pfade bei neuen Backend-Calls bedacht (Timeout / Retry / CircuitBreaker / Bulkhead).

### Konfiguration & Dependencies (falls betroffen)

- [ ] Dependency-Updates ausschließlich in `versions.properties` / `gradle/libs.versions.toml` (nicht direkt in `build.gradle.kts`).
- [ ] Neue Env-Vars / Properties sind dokumentiert (`README.md`, `application-*.yml`, `@ConfigurationProperties` mit `@Validated`).
- [ ] Refresh-Versions / Dependabot-Reviewer haben keine konkurrierenden offenen PRs auf denselben Konfigurationsbereich.

## Migrations- und Deployment-Hinweise

<!--
Wenn dieser PR beim Deploy besondere Aufmerksamkeit braucht, hier notieren:
- neue Pflicht-Env-Var
- Reihenfolge beim Ausrollen (z. B. Flyway-Schritt 1 vor Backend-Deploy, Schritt 2 nach Frontend-Deploy)
- Cache zu invalidieren / Redis-Keys zu löschen
- manuelle Schritte für Admins (Keycloak-Realm, UEX-API-Key, ...)
- Backwards-Compat-Zeitfenster und Sunset-Datum

Wenn nichts zu beachten ist: "Keine."
-->

## Screenshots / Demos (optional)

<!-- Für UI-Änderungen: vorher/nachher, möglichst pro Device-Klasse. -->

## Reviewer-Hinweise

<!--
Worauf sollte der Reviewer besonders schauen? Welche Teile sind subtil?
Wo wurden bewusst Trade-offs gemacht? Was ist explizit NICHT Teil dieses
PRs und kommt später (mit Link auf das Folge-Issue)?
-->
