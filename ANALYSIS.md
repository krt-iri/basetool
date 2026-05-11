# Projekt-Analyse — CLAUDE.md-Compliance & Best Practices

**Datum:** 2026-05-11
**Branch:** `claude/confident-pare-b3bcf6`
**Umfang:** Backend (`backend/`), Frontend (`frontend/`), Build, Infrastruktur, Doku.
**Methodik:** Statische Code-Analyse, Spot-Checks an realen Dateien, Abgleich mit den in `CLAUDE.md` formulierten Regeln. Keine Tests ausgefuehrt (per Regel: nur `./gradlew` mit ausdruecklicher Genehmigung).

> **Hinweis:** Aenderungen werden **erst nach ausdruecklicher Genehmigung** durch den Nutzer durchgefuehrt. Dieses Dokument ist eine reine Bestandsaufnahme + Verbesserungsvorschlaege.

---

## TL;DR — Gesamtbild

Das Projekt ist insgesamt **in einem sehr guten Zustand**. Die in CLAUDE.md beschriebenen Architekturregeln (Modul-Trennung Backend/Frontend, OAuth2/Keycloak, RFC 7807 GlobalExceptionHandler, Optimistic Locking via `@Version`, MapStruct, Resilience4j, MDC-Logging) sind durchgaengig und konsistent implementiert. Die Hauptbefunde liegen in folgenden Bereichen:

| Severity | Bereich | Anzahl Findings |
|---|---|---|
| **Hoch** | Tests fehlen fuer neues Promotion-System (V72), Tests laufen gegen H2 (nicht Postgres) | 2 |
| **Mittel** | `alert()`/`confirm()` in Templates, i18n-Umlaute in `.properties`, hardcodierte Strings in Java, Strukturfehler im Test-Ordner | 5 |
| **Niedrig** | Dockerfile EXPOSE-Port falsch, hardcodierte Bibliotheksversionen, kleinere Inkonsistenzen | 6 |

Insgesamt **13 priorisierte Findings** + einige optionale Verbesserungen.

---

## 1. KRITISCH / HOCH

### 1.1 Strukturfehler: Fehlbenannter Test-Ordner [HOCH]

**Datei:** [`backend/src/test/java/de/greluc.krt.iri.basetool.backend/GenerateSchema.java`](backend/src/test/java/de/greluc.krt.iri.basetool.backend/GenerateSchema.java)

Der Ordnername enthaelt **Punkte statt Slashes**: `de.greluc.krt.iri.basetool.backend` ist ein einzelner Verzeichnisname und kein Paketpfad. Die Datei ist zudem leer (0 Bytes).

- **Auswirkung:** Java findet diese Klasse nicht im erwarteten Package, sie wird vom Compiler ignoriert oder es entstehen Probleme bei `./gradlew test` (Source-Set-Auflistung).
- **Vorschlag:** Entweder die leere Datei loeschen oder den Ordner korrekt zu `backend/src/test/java/de/greluc/krt/iri/basetool/backend/` umstrukturieren und `GenerateSchema.java` mit sinnvollem Inhalt fuellen oder ebenfalls loeschen.

### 1.2 Tests fehlen fuer das gesamte Promotion-System [HOCH]

**Kontext:** Migration `V72__add_promotion_system.sql` fuegt 5 neue Tabellen ein (PromotionTopic, PromotionCategory, PromotionLevelContent, RankRequirement, MemberEvaluation). Es gibt entsprechende Entities, Repositories, Services und Controller im Backend (`PromotionTopicController`, etc.).

- **Problem:** Keine einzige `*Test.java`-Datei fuer diese Klassen. CLAUDE.md sagt: *"Every new feature ships with tests. No exceptions."*
- **Auswirkung:** Hohes Regressionsrisiko fuer neue, ungetestete Logik.
- **Vorschlag:** Mindestens Service-Unit-Tests (Mockito) + ein Controller-Slice-Test (`@WebMvcTest`) pro Controller anlegen.

**Zusaetzlich fehlen Tests fuer (Auswahl der wichtigsten):**

| Komponente | Datei (Hauptklasse) |
|---|---|
| `AdminController` | `backend/.../controller/AdminController.java` |
| `JobOrderController` | `backend/.../controller/JobOrderController.java` |
| `MissionController` | `backend/.../controller/MissionController.java` |
| `OperationController` | `backend/.../controller/OperationController.java` |
| `PersonalInventoryController` | `backend/.../controller/PersonalInventoryController.java` |
| `RefineryOrderController` | `backend/.../controller/RefineryOrderController.java` |
| `UserController` | `backend/.../controller/UserController.java` |
| `MissionService` | `backend/.../service/MissionService.java` |
| `KeycloakService` | `backend/.../service/KeycloakService.java` |
| `UexRefinerySyncService` | `backend/.../service/UexRefinerySyncService.java` |

Insgesamt: **~16 Services und 27 Controller** ohne explizite `*Test`-Datei.

### 1.3 Tests laufen gegen H2 statt Postgres + Flyway disabled in Tests [HOCH]

**Datei:** [`backend/src/main/resources/application-test.yml:6-16`](backend/src/main/resources/application-test.yml)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;...
    driverClassName: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop          # statt validate!
  flyway:
    enabled: false                    # Migrationen werden uebersprungen
```

- **Problem 1:** Die Test-DB ist H2, nicht Postgres. Postgres-spezifische Syntax (z. B. `JSONB`, `GIN`-Indexe, `ON CONFLICT`-Klauseln, `gen_random_uuid()`, Window-Funktionen) wird in Tests **nicht** validiert.
- **Problem 2:** Flyway ist disabled, d. h. die Migrationen werden **gar nicht** ausgefuehrt. Schema-Drift zwischen Migration und Entity-Annotationen wird durch Tests nicht erkannt — die Test-DB wird per `ddl-auto: create-drop` aus den Entities erzeugt, die Prod-DB aus Flyway. Das ist genau das in CLAUDE.md beschriebene "mock/prod divergence"-Risiko.
- **Vorschlag:**
  - Standard-Tests auf Testcontainers (Postgres) umstellen — entsprechende Dependencies sind bereits in `build.gradle.kts` (Zeile 73-74).
  - `ddl-auto: validate` + `flyway.enabled: true` im Test-Profil.
  - Wenn aus Geschwindigkeitsgruenden manche Unit-Tests weiterhin gegen H2 laufen sollen, in einem separaten Profil (`unit-fast`) trennen und die "echten" Tests gegen Postgres laufen lassen.
  - Existierender Praezedenzfall: `DatabaseIndexMigrationTest` nutzt bereits Testcontainers, aber nur wenn die ENV-Variable `ENABLE_TC=true` gesetzt ist. Diese Schwelle senken.

---

## 2. MITTEL

### 2.1 Native Browser-Dialoge (`alert()` / `confirm()`) in Templates [MITTEL]

CLAUDE.md: *"Never use confirm(), alert(), or any native browser dialog."*

**Funde (9 Stellen, 3 Dateien):**

| Datei | Zeile | Code |
|---|---|---|
| [`frontend/src/main/resources/templates/admin/materials.html:82`](frontend/src/main/resources/templates/admin/materials.html:82) | 82 | `onclick="return confirm(this.getAttribute('data-confirm'));"` |
| [`frontend/src/main/resources/templates/inventory-my.html:1195`](frontend/src/main/resources/templates/inventory-my.html:1195) | 1195 | `alert('[[#{success.inventory.update}]]');` |
| [`frontend/src/main/resources/templates/inventory-my.html:1201`](frontend/src/main/resources/templates/inventory-my.html:1201) | 1201 | `alert('[[#{error.inventory.update.conflict}]]');` |
| [`frontend/src/main/resources/templates/inventory-my.html:1208`](frontend/src/main/resources/templates/inventory-my.html:1208) | 1208 | `alert('[[#{error.inventory.update.failed}]]');` |
| [`frontend/src/main/resources/templates/inventory-my.html:1216`](frontend/src/main/resources/templates/inventory-my.html:1216) | 1216 | `alert('[[#{error.inventory.update.failed}]]');` |
| [`frontend/src/main/resources/templates/inventory-admin.html:1086`](frontend/src/main/resources/templates/inventory-admin.html:1086) | 1086 | `alert(...)` |
| [`frontend/src/main/resources/templates/inventory-admin.html:1092`](frontend/src/main/resources/templates/inventory-admin.html:1092) | 1092 | `alert(...)` |
| [`frontend/src/main/resources/templates/inventory-admin.html:1099`](frontend/src/main/resources/templates/inventory-admin.html:1099) | 1099 | `alert(...)` |
| [`frontend/src/main/resources/templates/inventory-admin.html:1107`](frontend/src/main/resources/templates/inventory-admin.html:1107) | 1107 | `alert(...)` |

**Kontext:** Die `alert()`-Aufrufe in den Inventory-Templates sind **defensive Fallbacks** fuer den Fall, dass `window.showFrontendSuccessToast`/`showFrontendErrorToast` nicht geladen ist. Strikt nach CLAUDE.md verstossen sie trotzdem gegen die Regel.

- **Vorschlag fuer Fallbacks:** Fallback durch `console.error(...)` ersetzen, alternativ einen minimalen Inline-Toast-Builder (analog `toast.html` Fragment Zeile 133-140) inline schreiben.
- **Vorschlag fuer `materials.html:82`:** Das KRT-Confirm-Modal aus dem `toast.html`-Fragment nutzen (siehe Vorbild [`personal-inventory.html:154`](frontend/src/main/resources/templates/personal-inventory.html:154) — *"Delete confirmation modal (KRT-styled, replaces native confirm())"*).

### 2.2 Umlaute in `.properties`-Dateien literal statt `\uXXXX` [MITTEL]

CLAUDE.md: *"In `.properties` files, German umlauts (`ae oe ue Ae Oe Ue ss`) MUST be encoded as `\uXXXX`."*

**Funde:**

| Datei | Zeile | Wert |
|---|---|---|
| [`frontend/src/main/resources/messages.properties:9`](frontend/src/main/resources/messages.properties:9) | 9 | `Fuehrungsposition` (literal "Fuehrung") |
| [`frontend/src/main/resources/messages.properties:371`](frontend/src/main/resources/messages.properties:371) | 371 | `Qualitaet` |
| [`frontend/src/main/resources/messages.properties:771`](frontend/src/main/resources/messages.properties:771) | 771 | `Qualitaet` |
| [`frontend/src/main/resources/messages_de.properties:9`](frontend/src/main/resources/messages_de.properties:9) | 9 | `Fuehrungsposition` |
| [`frontend/src/main/resources/messages_de.properties:329`](frontend/src/main/resources/messages_de.properties:329) | 329 | `Qualitaet` |
| [`frontend/src/main/resources/messages_de.properties:478`](frontend/src/main/resources/messages_de.properties:478) | 478 | `Persoenliches Inventar` |
| [`frontend/src/main/resources/messages_de.properties:704`](frontend/src/main/resources/messages_de.properties:704) | 704 | `Qualitaet` |
| [`frontend/src/main/resources/messages_en.properties:328`](frontend/src/main/resources/messages_en.properties:328) | 328 | `Qualitaet` |
| [`frontend/src/main/resources/messages_en.properties:703`](frontend/src/main/resources/messages_en.properties:703) | 703 | `Qualitaet` |

- **Ersetzungen:** `ae`->`ä`, `oe`->`ö`, `ue`->`ü`, `Ae`->`Ä`, `Oe`->`Ö`, `Ue`->`Ü`, `ss`->`ß`.
- **Anmerkung:** Spring Boot kann seit langem UTF-8 in `.properties` lesen, daher funktioniert es vermutlich heute zufaellig. Aber Regelverstoss bleibt; ausserdem ist die Encoding-Konvention ein Schutz gegen tooling, das die Datei mit Latin-1 oeffnet (insb. ueber IDE-Plugins).

### 2.3 Hardcodierte englische Strings in Frontend-Controller [MITTEL]

CLAUDE.md: *"Every user-visible string comes from `messages.properties` (...) No exceptions."*

**Funde:**

| Datei | Zeile | Code |
|---|---|---|
| [`frontend/src/main/java/.../controller/AdminMaterialsPageController.java:76`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/controller/AdminMaterialsPageController.java:76) | 76 | `redirectAttributes.addFlashAttribute("errorToast", "Error creating category");` |
| [`frontend/src/main/java/.../controller/AdminMaterialsPageController.java:88`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/controller/AdminMaterialsPageController.java:88) | 88 | `redirectAttributes.addFlashAttribute("errorToast", "Error deleting category");` |

- **Vorschlag:** Durch i18n-Keys ersetzen — der Erfolgs-Pfad in derselben Methode verwendet bereits korrekt `"notification.success.save"`/`"notification.success.delete"`. Analog z. B. `"notification.error.materials.category.create"` und `"notification.error.materials.category.delete"` definieren.

### 2.4 Hardcodierte Englische Fehler-Detail-Strings im GlobalExceptionHandler [MITTEL]

Die Strings in [`GlobalExceptionHandler.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/exception/GlobalExceptionHandler.java) sind komplett hardcodiertes Englisch:

```java
"The resource has been updated by another user. Please reload and try again."  // Zeile 151
"You must be signed in to perform this action."                                // Zeile 182
"You are not authorized to perform this action."                               // Zeile 205
```

- **Kontext:** Das Frontend wertet das `code`-Feld aus dem ProblemDetail aus und ersetzt den `detail`-Text dort durch eine lokalisierte Version — die `detail`-Strings landen also normalerweise nicht beim Endnutzer.
- **Aber:** OpenAPI-Konsumenten und API-Direktclients sehen genau diese englischen Strings. Fuer die Sauberkeit waere eine ResourceBundleMessageSource-Lookup ueber den `code` sinnvoll.
- **Niedrige Prio**, weil das Frontend uebersetzt — eher als "Verbesserung" denn "Verstoss" einzuordnen.

### 2.5 ResponseStatusException-Verwendung statt domaenenspezifischer Exceptions [MITTEL]

**Datei:** [`backend/src/main/java/.../service/JobOrderHandoverReportService.java:72,75`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/JobOrderHandoverReportService.java:72)

```java
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover not found");
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover does not belong to this job order");
```

- **Insgesamt 124 `ResponseStatusException`-Verwendungen in 20 Services/Controllern.**
- **Problem:** Die Reason-Strings sind hardcodiert Englisch + es bestehen mit `NotFoundException`, `DuplicateEntityException`, `EntityInUseException` bereits domaenenspezifische Exceptions im Projekt. Letztere fuehren zu konsistenten RFC-7807-Codes (`NOT_FOUND` etc.).
- **Vorschlag:** Wo moeglich `NotFoundException` statt `ResponseStatusException` werfen (`GlobalExceptionHandler.handleNotFound` ist bereits implementiert). Die Reason-Texte (`"Handover not found"`) sind dann nicht mehr im Code sondern in einer i18n-Resource.
- Bei `BAD_REQUEST`-Faellen ggf. eine `BadRequestException` einfuehren statt `ResponseStatusException`.

### 2.6 `frontend` ohne CycloneDX-SBOM [MITTEL]

Backend hat `cyclonedxBom`-Task in [`backend/build.gradle.kts:139`](backend/build.gradle.kts:139), Frontend nicht. Beide Module werden produktiv ausgerollt — fuer Supply-Chain-Transparenz (insb. mit Logstash, Resilience4j, Caffeine, Redis-Client) sollte auch das Frontend einen SBOM produzieren.

---

## 3. NIEDRIG

### 3.1 Dockerfile EXPOSE-Port falsch [NIEDRIG]

**Datei:** [`backend/Dockerfile:53`](backend/Dockerfile:53)

```dockerfile
EXPOSE 10261
```

Der Backend-Service lauscht auf Port **11261** (siehe `application.yml:8` und `docker-compose.yml:70`).

- **Auswirkung:** Eher kosmetisch (Docker leitet `EXPOSE` nur als Metadaten weiter, der eigentliche Port-Bind passiert via `-p`/Compose). Aber das ist verwirrend.
- **Fix:** `EXPOSE 11261`.

### 3.2 Hardcodierte Bibliotheksversionen im Backend [NIEDRIG]

CLAUDE.md: *"Dependencies are managed by refreshVersions — edit `versions.properties`, not `build.gradle.kts`."*

| Datei | Zeile | Code |
|---|---|---|
| [`backend/build.gradle.kts:61`](backend/build.gradle.kts:61) | 61 | `compileOnly("org.jetbrains:annotations:26.1.0")` |
| [`frontend/build.gradle.kts:40`](frontend/build.gradle.kts:40) | 40 | `compileOnly("org.jetbrains:annotations:26.0.2")` |

- **Zusatzproblem:** Die beiden Module nutzen **unterschiedliche** Versionen (26.0.2 vs. 26.1.0).
- **Fix:** Eintrag `version.org.jetbrains..annotations=26.1.0` in [`versions.properties`](versions.properties) hinzufuegen, in den Builds dann `compileOnly("org.jetbrains:annotations:_")`.

### 3.3 `tasks.withType<Test>` und `BootRun` dupliziert [NIEDRIG]

Backend und Frontend haben quasi identische Mockito-Agent-Bloecke in `tasks.withType<Test>`. Konnte in `gradle/` als Convention-Plugin (z. B. `basetool.java-conventions.gradle.kts`) zusammengefasst werden. Reine Wartbarkeits-Verbesserung.

### 3.4 Frontend-Healthcheck im Dockerfile fehlt [NIEDRIG]

Beide [`backend/Dockerfile`](backend/Dockerfile) und [`frontend/Dockerfile`](frontend/Dockerfile) enthalten keinen `HEALTHCHECK`. Docker Compose `depends_on` mit `condition: service_healthy` waere damit nicht moeglich. Spring Boot Actuator-Endpoints (Backend hat `actuator` als Dependency) waeren verfuegbar.

- **Vorschlag:**
  ```dockerfile
  HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
      CMD curl -fsS https://localhost:11261/actuator/health -k || exit 1
  ```

### 3.5 Timezone-Serialization-Tests sind minimal [NIEDRIG]

CLAUDE.md: *"Write serialization tests for timezone behavior."*

[`InstantSerializationTest.java`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/InstantSerializationTest.java) testet nur **einen** Happy Path (Instant -> JSON-String). Keine Tests fuer:

- `OffsetDateTime`-Serialisierung.
- Deserialisierung (`Instant` aus `2026-03-03T12:00:00Z` lesen).
- `LocalDateTime` aus `HandoverReportPreviewRequestDto` (CLAUDE.md sagt: "Convert to the user's local timezone in the display layer only" — diese DTO macht das gezielt anders, das sollte abgesichert sein).
- Edge-Cases: positive/negative Offsets, Sommer-/Winterzeit.

### 3.6 `ResponseStatusException` mit englischen Reason-Strings im PDF-Service [NIEDRIG]

Bereits unter 2.5 erwaehnt — gleicher Befund.

---

## 4. POSITIVE BEFUNDE (was bereits sehr gut ist)

Dieser Abschnitt dient nicht der Kritik, sondern zeigt, was die Codebasis bereits stark macht:

1. **Multi-User-Datenisolation:** Strikt eingehalten in `PersonalInventoryItemService`, `MissionController.cleanupMissionForGuest()`, `MissionSecurityService.canEditFinanceEntry()`. Saemtliche Service-Methoden filtern auf JWT-`sub` ohne Loecher.
2. **Authorization-Placement:** `@PreAuthorize` durchgaengig auf Controllern und Services (140 Vorkommen in 27 Controller-Dateien). Roles als `ROLE_*` uppercased.
3. **API-Versionierung:** Alle Endpoints unter `/api/v1/...`, deprecated Endpoints via `@ApiDeprecation` (siehe [`SystemController.java:24-25`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/SystemController.java:24)).
4. **DTOs als Records:** Alle 107 DTOs sind Records — keine class-based DTOs gefunden.
5. **Konstruktor-Injection ueberall:** Kein `@Autowired`-Feld in Production-Code.
6. **`@Slf4j` durchgaengig:** Keine manuellen `LoggerFactory.getLogger()`-Aufrufe.
7. **RFC 7807:** Sehr saubere zentrale Implementierung in `GlobalExceptionHandler` mit `code`+`correlationId` als Extension Properties (Zeile 38-56). Trennt 4xx (WARN, kein Stacktrace) sauber von 5xx (ERROR, full Stacktrace).
8. **PII-Masking:** Echte Implementierung in [`PiiMasker.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/logging/PiiMasker.java) maskiert JWTs, Emails, Bearer-Token in Logs.
9. **Correlation ID:** [`CorrelationIdFilter.java:86-96`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/logging/CorrelationIdFilter.java:86) validiert via `isSafe()` (Log-Injection-Schutz), max. 128 Zeichen.
10. **Resilience4j:** `BackendApiClient` wrappt jeden Call mit `@Retry` + `@CircuitBreaker`, externer Fallback und Exception-Handling vorhanden.
11. **Optimistic Locking:** `@Version` in `AbstractEntity` ([`AbstractEntity.java:23`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/model/AbstractEntity.java:23)) wird durchgaengig vererbt. Documentation der `*WithinTransaction`-Patterns im CLAUDE.md ist exemplarisch.
12. **Flyway-Migrationen:** 72 Migrationen, durchnummeriert ohne Luecken, alle nach `V<n>__<beschreibung>.sql` benannt, `ddl-auto: validate` in Prod/Dev.
13. **OpenAPI-Annotationen:** Konsistent (15+ Controller mit `@Operation`/`@ApiResponses`).
14. **Pagination:** `PageResponse`-Wrapper + Sort-Whitelist in `PaginationUtil`.
15. **CHANGELOG.md** wird konsistent gepflegt (75 KB, deckt alle aktuellen Commits ab).
16. **Bot-Schutz & SSO-Re-Auth:** `BotProtectionFilter` mit umfassender Whitelist, `SsoReAuthenticationEntryPoint` mit silent `prompt=none`-Redirect und SSO_ATTEMPTED-Cookie (60 s) gegen Endlosschleifen.
17. **Type-safe Config:** `@ConfigurationProperties` + `@Validated` mit `@NotBlank`/`@URL`/`@Min`/`@Max` in `LoggingProperties`, `KeycloakSyncProperties`, `RateLimitProperties`, `UexProperties`, `AppProblemProperties`. Keine plain-text Secrets in YAML committed.
18. **Versions-Katalog:** `gradle/libs.versions.toml` + `versions.properties` korrekt eingerichtet (refreshVersions-Konvention).

---

## 5. BEST-PRACTICE-VORSCHLAEGE (ueber CLAUDE.md hinaus)

Diese Punkte sind nicht streng durch CLAUDE.md gefordert, sondern allgemeine Verbesserungen:

### 5.1 Statische Code-Analyse im Build

- **SpotBugs** + **PMD** + **Checkstyle** ueber `./gradlew check`, optional **Error Prone**.
- **OWASP Dependency-Check** (`org.owasp.dependencycheck`-Plugin) parallel zum CycloneDX-SBOM — meldet bekannte CVEs in Dependencies bei jedem Build.

### 5.2 JaCoCo auch im Backend

Frontend hat bereits `id("jacoco")` ([`frontend/build.gradle.kts:3`](frontend/build.gradle.kts:3)). Backend nicht — angesichts der mehr Geschaeftslogik im Backend sollte dort die Coverage transparenter werden, mindestens als Report-Output.

### 5.3 Mutation Testing

PIT (Pitest) ueber `./gradlew :backend:pitest` waere fuer die Kern-Services (`MissionService`, `JobOrderService`, `PersonalInventoryItemService`) eine kosteneffiziente Methode, um Test-Quality zu validieren.

### 5.4 Migration-Konventionen formalisieren

`backend/src/main/resources/db/migration/README.md` mit:

- Verbot von `DROP TABLE`/`DROP COLUMN` ohne `V<n+1>` als "Cleanup"-Migration nach Grace Period.
- Konvention zur Datenmigration (z. B. UPDATE-Statement im Migration-Skript).
- Hinweis, dass Flyway-Migrationen in Tests laufen muessen (siehe 1.3).

### 5.5 Pre-Commit-Hooks

Pre-commit Hook der mindestens prueft:

- `.properties`-Dateien enthalten keine literalen Umlaute.
- Keine `confirm(`/`alert(` in `static/js/` und `templates/`.
- Keine `LocalDateTime` ausserhalb der zwei explizit erlaubten Klassen.

### 5.6 OpenAPI-Spec im CI generieren und committen

[`backend/src/main/resources/api/openapi.json`](backend/src/main/resources/api/openapi.json) sollte automatisch generiert (per Test in `MockMvc`-Run) und gegen die committed Version verglichen werden — sonst driftet die Spec von der Implementierung.

### 5.7 ArchUnit-Tests

Mit ArchUnit (1-2 Klassen) kannst du die in CLAUDE.md genannten Regeln **maschinell** durchsetzen:

- "Frontend ruft nie `JpaRepository` direkt".
- "Controller geben nie `@Entity`-Klassen zurueck".
- "`SecurityContextHolder` nicht in `service/business/`-Subpaket".
- "Alle REST-Controller haben `@PreAuthorize` oder `@PermitAll`".

### 5.8 README-Doku zu `keycloak-theme/`

Das `keycloak-theme/krt-theme/`-Verzeichnis ist in der README nicht erwaehnt — fuer Onboarding waere ein kurzer Abschnitt hilfreich (was muss bei Theme-Aenderungen passieren? Wird das beim Keycloak-Container automatisch eingebunden?).

---

## 6. PRIORISIERTE TODO-LISTE (Vorschlag)

Wenn du die Aenderungen freigibst, schlage ich diese Reihenfolge vor:

| # | Prio | Aufwand | Aufgabe |
|---|---|---|---|
| 1 | HOCH | < 5 min | Leerdatei + Misnamed Folder `de.greluc.krt.iri.basetool.backend/` loeschen (1.1) |
| 2 | HOCH | < 10 min | Dockerfile EXPOSE 10261 -> 11261 (3.1) |
| 3 | HOCH | ~15 min | Umlaute in 3 messages.properties-Dateien -> `\uXXXX` umstellen (2.2) |
| 4 | HOCH | ~15 min | JetBrains-Annotations Version in `versions.properties` zentralisieren + auf gleiche Version bringen (3.2) |
| 5 | MITTEL | ~30 min | Hardcodierte Strings in `AdminMaterialsPageController.java` durch i18n-Keys ersetzen (2.3) |
| 6 | MITTEL | ~1 h | `confirm()`/`alert()` in 3 Templates durch KRT-Modal/Toast oder `console.error` ersetzen (2.1) |
| 7 | MITTEL | ~30 min | `ResponseStatusException(NOT_FOUND, ...)` durch existierende `NotFoundException` ersetzen, Reason-Texte i18n (2.5) |
| 8 | MITTEL | ~30 min | CycloneDX-Plugin im Frontend aktivieren (2.6) |
| 9 | MITTEL | ~1-2 h | Healthchecks in beide Dockerfiles aufnehmen (3.4) |
| 10 | MITTEL | ~3-4 h | Testcontainers (Postgres) + Flyway als Standard im Test-Profil aktivieren (1.3) |
| 11 | HOCH | ~1-2 Tage | Tests fuer Promotion-System (Services + Controller) anlegen (1.2) |
| 12 | NIEDRIG | ~1 h | Timezone-Tests fuer `OffsetDateTime` + Deserialisierung erweitern (3.5) |
| 13 | NIEDRIG | optional | ArchUnit-Regeln + JaCoCo Backend + Pre-Commit-Hooks (5.x) |

---

## 7. FRAGEN AN DEN NUTZER

Bevor ich konkret etwas aendere, benoetige ich Entscheidungen bei diesen Punkten:

1. **Soll ich die "schnellen Wins" (1.1, 3.1, 2.2, 3.2, 2.3) zusammen in einem PR umsetzen?** Oder einzeln?
2. **Test-Migration auf Testcontainers (1.3) ist groesser** — soll das ein eigenes, abgegrenztes Vorhaben werden? Die bestehende H2-Konfiguration ggf. als `unit-fast`-Profil behalten?
3. **Promotion-System-Tests (1.2):** Gibt es bereits Akzeptanzkriterien / Use-Cases dafuer, oder soll ich generische Service-Unit-Tests + Controller-Slice-Tests anlegen?
4. **`alert()`-Fallback (2.1):** Lieber `console.error()` als stiller Fallback, oder einen Inline-Mini-Toast?
5. **`ResponseStatusException` -> `NotFoundException` (2.5):** Komplette Refactoring-Welle in allen 20 Dateien, oder erstmal nur den `JobOrderHandoverReportService`?

Sobald du die gewuenschten Punkte freigibst, gehe ich sie in der von dir bevorzugten Reihenfolge an.
