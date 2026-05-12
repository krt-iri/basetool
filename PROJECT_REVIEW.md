# Projekt-Review — Fehlerquellen & Best-Practice-Audit

**Datum:** 2026-05-12
**Branch:** `claude/intelligent-euler-54b76b`
**Umfang:** Backend (`backend/`), Frontend (`frontend/`), Build/Infrastruktur, Datenbank, Tests, Doku.
**Methodik:** Statische Code-Analyse durch parallele spezialisierte Agenten + manuelle Verifikation der kritischsten Funde. Keine Tests ausgefuehrt, keine Aenderungen am Code vorgenommen (per Nutzer-Anweisung).
**Bezug:** Eine fruehere Analyse `ANALYSIS.md` (Datum 2026-05-11) existiert. Mehrere der dort genannten Punkte sind inzwischen behoben (siehe Abschnitt 6).

> **Hinweis:** Aenderungen werden erst nach ausdruecklicher Genehmigung umgesetzt. Dieses Dokument ist eine reine Bestandsaufnahme.

---

## 0. Executive Summary

Das Projekt ist insgesamt **in einem sehr guten und gepflegten Zustand**. Architektur, Sicherheits-Layer, Optimistic-Locking-Disziplin, RFC-7807-Fehlerbehandlung, i18n, PII-Masking, Flyway-Migrationen und Test-Patterns sind durchgaengig professionell. Die meisten in der vorherigen ANALYSIS.md genannten Probleme (Promotion-Tests, `ResponseStatusException`-Wildwuchs, `alert()`-Aufrufe, Umlaute in `.properties`, Dockerfile EXPOSE 10261) sind inzwischen behoben.

**Restliche Befunde (priorisiert):**

| Severity | Bereich | Anzahl |
|---|---|---|
| **Hoch** | Architektur-Verstoesse (SecurityContextHolder in Controllern/Mapper), `RuntimeException` in Services, Tests nur gegen H2 | 4 |
| **Mittel** | OWASP-Gate effektiv aus, CSP-`unsafe-inline` fuer Inline-Handler, JaCoCo nur Frontend, fehlende Docker-Resource-Limits, public POST/DELETE auf state-changing Endpoints | 6 |
| **Niedrig** | duplizierte Gradle-Bloecke, hardcodierte Magic-Numbers, ArchUnit-Version hardcoded, kleinere Codeglaettungen | ~10 |

---

## 1. KRITISCH / HOCH

### 1.1 Architektur — `SecurityContextHolder` ausserhalb der Auth-Helper-Klasse [HOCH]

CLAUDE.md / ArchUnit-Regel: *"Service-Layer-Code beruehrt `SecurityContextHolder` nicht direkt — einzige Ausnahme: `UserService`."* Diese Regel wird durch [`backend/.../ArchitectureTest.java:62-80`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java:62) maschinell geprueft — aber nur fuer das `..service..`-Package.

**Loecher in der ArchUnit-Abdeckung (verifiziert):**

| Datei | Zeilen | Befund |
|---|---|---|
| [`backend/.../mapper/MissionMapper.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/mapper/MissionMapper.java) | 16, 43, 52, 58 | `@Autowired protected MissionSecurityService` (Field-Injection!) + drei `SecurityContextHolder.getContext().getAuthentication()`-Aufrufe in Mapper-Resolver-Methoden. MapStruct-Mapper sollen reine Transformatoren sein. |
| [`backend/.../controller/JobOrderController.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/JobOrderController.java:254) | 254 | `SecurityContextHolder.getContext().getAuthentication()` in `verifyAssigneeAccess()` (Privat-Helper im Controller). |
| [`backend/.../controller/InventoryItemController.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/InventoryItemController.java:190) | 190 | `isLogisticianOrAbove()` im Controller liest `SecurityContextHolder`. |
| [`backend/.../controller/HangarController.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/HangarController.java) | div. | direkter `SecurityContextHolder`-Zugriff. |
| [`backend/.../controller/RefineryOrderController.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/RefineryOrderController.java:132) | 132 | dito. |
| [`backend/.../service/MissionFinanceEntryService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/MissionFinanceEntryService.java) | — | `SecurityContextHolder` im Service-Layer — sollte ArchUnit-Regel verletzen. Pruefen, ob dies durch Allow-Liste abgedeckt ist oder ob die Architektur-Tests rot werden. |

**Auswirkung:** Verteilte Zustandig­keit fuer Auth-Entscheidungen, schwer testbar (jeder Test braucht vollen Security-Context), Bypass moeglich wenn die gleiche Methode aus Scheduling/async aufgerufen wird.

**Vorschlag:** Zwei Aufgaben — (a) Einen `AuthHelperService` einfuehren (oder `UserService` erweitern), der die Role-Hierarchy-Checks und Owner-Lookups buendelt; (b) ArchUnit-Regel auf `..mapper..` und `..controller..` ausweiten — entweder gleicher Test mit weiterem Package, oder eine zweite Regel, die `mapper`-Klassen darauf prueft. Die Mapper-Resolver-Logik gehoert ohnehin nicht in den Mapper, sondern in den Controller bzw. Service.

---

### 1.2 Domaenenspezifische Exceptions statt `RuntimeException` [HOCH]

CLAUDE.md / RFC 7807: *"Extend `GlobalExceptionHandler` rather than throwing into the void; problem-type URIs come from `AppProblemProperties`, not hardcoded strings."*

**Funde (verifiziert):**

| Datei | Zeile | Code |
|---|---|---|
| [`backend/.../service/KeycloakService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/KeycloakService.java:109) | 109 | `throw new RuntimeException("Could not retrieve access token from Keycloak. Response: " + response);` |
| [`backend/.../service/KeycloakService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/KeycloakService.java:111) | 111 | `throw new RuntimeException("Keycloak returned error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);` |
| [`backend/.../service/RefineryOrderService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/RefineryOrderService.java:81) | 81 | `throw new RuntimeException("Location is required");` |
| [`backend/.../service/RefineryOrderService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/RefineryOrderService.java:127) | 127 | `throw new RuntimeException("Input Material is required for refined goods");` |
| [`backend/.../service/RefineryOrderService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/RefineryOrderService.java:226) | 226 | dito (Update-Pfad). |
| [`backend/.../service/JobOrderHandoverReportService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/JobOrderHandoverReportService.java:240) | 240 | `throw new RuntimeException("PDF generation failed", e);` |

**Auswirkung:** `RuntimeException` faellt im `GlobalExceptionHandler` in den Catch-All `500` Pfad und verliert den semantischen Kontext. Die Reason-Strings sind zudem hardcodiertes Englisch. Vorhandene `BadRequestException` / `NotFoundException`-Hierarchie ist da — wird hier umgangen.

**Vorschlag:**
- RefineryOrderService: `BadRequestException("refineryOrder.location.required")` etc. (i18n-Code).
- KeycloakService: Eine `ExternalServiceException` einfuehren, die im Handler zu `502/503` mappt, mit `code=KEYCLOAK_UNAVAILABLE`.
- JobOrderHandoverReportService: `ReportGenerationException` o.ae. — derzeit landet jeder Fehler als `500 Internal Server Error` mit kryptischer Detail-Message.

---

### 1.3 Tests laufen weiter gegen H2 statt Postgres + Flyway deaktiviert [HOCH]

**Datei:** [`backend/src/main/resources/application-test.yml`](backend/src/main/resources/application-test.yml) (verifiziert)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driverClassName: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

**Bestaetigung:** Die einzige Postgres-validierende Test-Klasse ist [`DatabaseIndexMigrationTest`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/db/DatabaseIndexMigrationTest.java) und wird durch `@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")` standardmaessig uebersprungen.

**Konsequenzen:**
- Postgres-spezifische Syntax (z. B. `JSONB`, `GIN`, `pg_trgm`, `ON CONFLICT`, `gen_random_uuid()`, Window-Funktionen aus V35) wird in PR-Tests **nicht** validiert.
- Schema-Drift zwischen Flyway-Migrationen und Entity-Annotationen ist in Tests unsichtbar: Test-DB wird aus Entities erzeugt, Prod-DB aus Flyway — exakt das in CLAUDE.md beschriebene "mock/prod divergence"-Risiko.
- Testcontainers-Dependencies sind bereits in [`backend/build.gradle.kts:77-78`](backend/build.gradle.kts:77) eingebunden — der Schritt ist sehr klein.

**Vorschlag:** Default-Profil auf Testcontainers/Postgres umstellen, mit `@Testcontainers` + `@DynamicPropertySource`. H2 bleibt als optionales `unit-fast`-Profil fuer reine Mockito-Unit-Tests. Migrationen MUESSEN in Tests laufen, sonst sind `validate`-Drift-Bugs unsichtbar.

---

### 1.4 ArchUnit-Regeln decken nicht alle Architektur-Invarianten ab [HOCH]

Die drei Regeln in [`ArchitectureTest.java`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java) sind wertvoll, aber **nicht erschoepfend** im Sinne von CLAUDE.md:

| Was die Regel prueft | Was sie nicht abfaengt |
|---|---|
| `SecurityContextHolder` in `..service..` | Mapper (`MissionMapper`) und Controller (`InventoryItemController`, `JobOrderController` etc.) — siehe 1.1 |
| Controller geben keine Entity zurueck (`raw return type`) | `ResponseEntity<Entity>` / `Page<Entity>` (ArchUnit kann die Generics-Argumente nicht so leicht erreichen) |
| Jeder `@RestController` hat min. ein `@PreAuthorize` | Es sagt nichts ueber **welche** Methoden geschuetzt sind — eine einzelne Methode reicht. Methoden-Granularitaet wird nicht erzwungen. |
| (fehlt) | Keine Regel "Service-Methoden mit Write-DTO benoetigen `@Transactional`". |
| (fehlt) | Keine Regel "Repositories duerfen nicht aus Controllern injiziert werden". |

**Vorschlag:** Drei neue Regeln (geringer Aufwand):
1. `..mapper..` und `..controller..` duerfen `SecurityContextHolder` ebenfalls nicht referenzieren (Allow-Liste fuer wenige berechtigte Ausnahmen).
2. `@RestController`-Methoden, die `@RequestBody`/`@RequestParam` haben, MUESSEN `@PreAuthorize` annotiert sein (klassenweite Annotation oder methodenweise).
3. `..controller..` darf nicht von `..repository..` abhaengen (Controller → Service → Repository).

---

## 2. MITTEL

### 2.1 OWASP Dependency-Check Gate effektiv deaktiviert [MITTEL]

[`build.gradle.kts:157`](build.gradle.kts:157):
```kotlin
failBuildOnCVSS = 11.0f
```

CVSS-Werte gehen nur bis 10.0 — der Build kann nie failen. Das ist nach CLAUDE.md / Doku-Notiz absichtlich als Initial-Stufe gewaehlt, sollte aber stufenweise gesenkt werden.

**Vorschlag:** Erster Schritt `9.0` (Critical only), nach Triage `7.5` (High+) und schliesslich `4.0`.

---

### 2.2 CSP erlaubt `script-src-attr 'unsafe-inline'` [MITTEL]

[`frontend/.../config/SecurityConfig.java:54-60`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/config/SecurityConfig.java:54)

```java
// The templates carry ~200 inline event-handler attributes (onclick="…", …).
// ... we keep them runnable via a separate, narrowly-scoped script-src-attr directive.
+ "script-src-attr 'unsafe-inline'";
```

`<script>`-Elemente sind nonce-gated (gut), aber alle `onclick=`/`onchange=`/`onsubmit=`-Attribute laufen ueber `'unsafe-inline'`. Solange irgendein `th:onclick`-Attribut einen vom Backend kommenden Wert interpoliert, ist das ein latentes XSS-Risiko.

**Vorschlag (laengere Aufgabe):** Migration zu `addEventListener` in dedizierten JS-Files. Bis dahin: Audit aller `th:onclick`/`onclick`-Attribute auf interpolierte Werte (CSS-/JS-Escape via `${#strings.escapeJs(...)}`).

---

### 2.3 Public state-changing Endpoints im Frontend [MITTEL]

[`frontend/.../SecurityConfig.java:115-120`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/config/SecurityConfig.java:115)

```java
.requestMatchers("/missions/**").permitAll()
.requestMatchers("/operations/**").permitAll()
.requestMatchers("/orders/**").permitAll()
```

Kombiniert mit den CSRF-Ausnahmen `/missions/**`, `/operations/**`, `/inventory/**` (Zeile 91-98) verlassen sich diese Pfade vollstaendig auf Service-Layer-Checks. Das ist konsistent dokumentiert (Gast-Lesezugriff auf Missionen), aber:

- **Schreibende POST/DELETE-Operationen** sind nicht zentral durch Spring-Security gesperrt — jede neue, nicht-gewartete Methode auf einem dieser Controller waere oeffentlich, wenn das `@PreAuthorize` vergessen wird. Hier wuerde die ArchUnit-Regel "Jeder Endpoint mit `@RequestBody` braucht `@PreAuthorize`" greifen (siehe 1.4).
- Rate-Limit `RateLimitingFilter` ist auf `/api/**` global mit 300/min — kein Per-IP-Bucket, kein striktes Limit auf Schreib-Pfade.

**Vorschlag:** Inventur aller Public-Pfade — fuer jeden POST/DELETE-Endpoint dokumentieren, welche Service-Methode die Owner-Pruefung uebernimmt. Anschliessend pro Schreibroute explizit `requestMatchers(POST, "/missions/**").authenticated()` (mit Public-Liste fuer Schaupfade).

---

### 2.4 Frontend: POST/PUT/DELETE im `BackendApiClient` ohne `@TimeLimiter` [MITTEL]

`BackendApiClient` umhuellt GET mit `@Retry + @CircuitBreaker + @TimeLimiter`, aber Schreib-Operationen nur mit `@CircuitBreaker`. Ohne `@TimeLimiter` kann eine haengende Backend-Antwort den Frontend-Thread blockieren (Bulkhead schuetzt vor Thread-Exhaustion, aber nicht vor blockierendem WebClient-Read).

**Vorschlag:** `@TimeLimiter` auch fuer Schreib-Methoden — Retries sind bewusst aus (Idempotenz), aber ein Timeout muss sein. WebClient-Read-Timeout in `AppHttpProperties` ist 5s — passt.

---

### 2.5 JaCoCo nur im Frontend, nicht im Backend [MITTEL]

[`frontend/build.gradle.kts:6`](frontend/build.gradle.kts:6) aktiviert `jacoco`. Backend nicht. Backend enthaelt aber die deutlich groessere Business-Logik. Ohne Coverage-Report kein Sichtbarkeit, welche Service-Methoden ungetestet sind.

**Vorschlag:** `id("jacoco")` in [`backend/build.gradle.kts`](backend/build.gradle.kts) ergaenzen. Die Subprojects-Konfiguration in [`build.gradle.kts:66-77`](build.gradle.kts:66) konfiguriert JaCoCo automatisch wenn das Plugin angewendet wird.

---

### 2.6 docker-compose: keine Resource-Limits [MITTEL]

[`docker-compose.yml`](docker-compose.yml) — keiner der Services hat `deploy.resources.limits.memory` oder `cpus` definiert. In Produktion (sogar mit `--profile prod`) kann ein einzelner Service den Host RAM monopolisieren (z. B. Heap-Leak im Backend → OOM-Killer schiesst Postgres).

**Vorschlag:** Mindestlimits — Backend `2g/2cpus`, Frontend `1g/1cpu`, Keycloak `2g/2cpus`, DBs `1g/1cpu`, Redis `512m/0.5cpu`. JVM-Optionen (`-XX:MaxRAMPercentage=75.0`) sind in `application-prod.yml` bereits dokumentiert.

---

### 2.7 Backend Dockerfile: kein Alpine-JRE [MITTEL — optional]

[`backend/Dockerfile:39`](backend/Dockerfile:39) nutzt `eclipse-temurin:25-jre-jammy` (~250 MB). `eclipse-temurin:25-jre-alpine` waere ~80 MB. Trade-off: Alpine nutzt musl libc — Spring Boot laeuft, aber selten genutzte JNI-Libs (z. B. native PDF-Renderer) koennten brechen.

**Vorschlag:** Erst pruefen, ob `openpdf` und der Tomcat-Native-Connector unter musl funktionieren. Wenn ja, Alpine. Wenn nein, `eclipse-temurin:25-jre-jammy-slim` (~150 MB) als Mittelweg.

---

### 2.8 Magic Numbers im Backend [MITTEL]

| Datei | Zeile | Wert |
|---|---|---|
| [`backend/.../service/JobOrderService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/JobOrderService.java) | 60, 244 | `.minQuality(750)` — kein `static final` |
| [`backend/.../service/JobOrderHandoverService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/JobOrderHandoverService.java) | 110, 124, 142 | Floating-Point-Epsilon `0.0001` ohne Konstante |
| [`backend/.../service/InventoryItemService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/InventoryItemService.java) | 416, 436 | gleiches `0.0001` Epsilon |

**Vorschlag:** Konstanten `private static final int MIN_QUALITY_DEFAULT = 750;` bzw. `private static final double QUANTITY_EPSILON = 1e-4;`. Bei `minQuality` ggf. nach `SystemSetting` verschieben, falls konfigurierbar gewuenscht.

---

### 2.9 `Optional.get()` ohne `orElseThrow` [MITTEL]

| Datei | Zeile | Befund |
|---|---|---|
| [`backend/.../service/HangarImportService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/HangarImportService.java) | 118 | `.get()` nach Filter — sicher, aber sollte `orElseThrow` werden |
| [`backend/.../service/AnnouncementService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/AnnouncementService.java) | 32 | `.get()` nach `isPresent()`-Check — Anti-Pattern |
| [`backend/.../service/MissionSecurityService.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/MissionSecurityService.java) | 135, 168, 204 | gleiches Pattern |

**Vorschlag:** `Optional.orElseThrow()` / `.map(...)`/`.ifPresent(...)` durchgehend nutzen. SpotBugs/PMD/SonarLint markieren `Optional.get()` standardmaessig als Code-Smell.

---

## 3. NIEDRIG

### 3.1 Duplizierte Bloecke in `backend/build.gradle.kts` [NIEDRIG]

Backend-Build hat zwei `java {}`-Bloecke ([Zeile 22-26](backend/build.gradle.kts:22) und [90-97](backend/build.gradle.kts:90)) und zwei `configurations { compileOnly { … } }`-Bloecke ([28-32](backend/build.gradle.kts:28) und [107-111](backend/build.gradle.kts:107)). Funktioniert wegen Gradle-Kotlin-DSL-Idempotenz, ist aber verwirrend.

**Vorschlag:** Auf je einen Block reduzieren.

---

### 3.2 ArchUnit-Version hardcoded statt Versions-Katalog [NIEDRIG]

[`backend/build.gradle.kts:85`](backend/build.gradle.kts:85) und [`frontend/build.gradle.kts:60`](frontend/build.gradle.kts:60):
```kotlin
testImplementation("com.tngtech.archunit:archunit:1.3.2")
```

Sollte in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) als `archunit-core = { ... }` Alias gepinnt sein — analog zu `mapstruct.core`, `bucket4j.core` etc.

---

### 3.3 SpringDoc und MockWebServer mit `_`-Wildcard [NIEDRIG]

[`backend/build.gradle.kts:47`](backend/build.gradle.kts:47): `org.springdoc:springdoc-openapi-starter-webmvc-ui:_`
[`frontend/build.gradle.kts:56`](frontend/build.gradle.kts:56): `com.squareup.okhttp3:mockwebserver:_`

Korrekt via `refreshVersions` (Eintrag in [`versions.properties:16`](versions.properties:16)), aber inkonsistent zu allen anderen Dependencies, die `libs.versions.toml` nutzen. Eine Konvention sollte gewaehlt werden.

---

### 3.4 `@ToString` ohne `exclude` auf `InventoryItem` [NIEDRIG]

[`backend/.../model/InventoryItem.java:13`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/model/InventoryItem.java:13) hat `@ToString` ohne Felder auszuschliessen. Falls eine LAZY-Relation (`jobOrder`, `mission`) in `toString()` referenziert wird und ausserhalb einer Session aufgerufen wird, triggert `LazyInitializationException`. Andere Entities (z. B. `Mission`) machen das richtig mit `@ToString(exclude = {...})`.

---

### 3.5 `Bearer ` String-Konstanten im `KeycloakService` [NIEDRIG]

[`backend/.../service/KeycloakService.java:39, 73`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/KeycloakService.java) setzt `"Bearer " + token` manuell. Spring HTTP-Header-Konstante `HttpHeaders.AUTHORIZATION` und `"Bearer "` als `private static final` waeren konsistenter. PII-Masker fangt es zwar ab, aber jede Manuelle-String-Konstruktion erhoeht das Risiko, dass irgendwer aus Versehen `log.debug("...Bearer " + token, ...)` schreibt.

---

### 3.6 Deutsche Inline-Kommentare in englischem Code-Kontext [NIEDRIG — Konsistenz]

[`backend/.../service/RefineryOrderService.java:137-144`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/RefineryOrderService.java:137) hat einen ausfuehrlichen deutschen Kommentar. Restlicher Code/Javadoc ist Englisch. Ist Geschmackssache, aber Konsistenz waere besser (CLAUDE.md trifft keine Festlegung — pragmatisch englisch belassen).

---

### 3.7 `System.out.println` in 13 Test-Dateien [NIEDRIG]

`OptimisticLockingTest:35,49`, `RefineryLocationTest`, `OfficerRefineryButtonsTest` u. a. — Debug-Prints. Sollten SLF4J nutzen oder ganz entfernt werden. Test-Output landet sonst zwischen JUnit-Reports.

---

### 3.8 `ConcurrencyTest` ist nicht wirklich concurrent [NIEDRIG]

`ConcurrencyTest` und `OptimisticLockingTest` simulieren sequenziell zwei Versionen, nicht echte Parallelitaet. Fuer echtes Locking-Verhalten waere `ExecutorService` + `CountDownLatch` noetig. Aktuell erkennen die Tests die meisten Race-Conditions, koennen aber Timing-abhaengige Bugs nicht reproduzieren.

---

### 3.9 ResourceBundle fuer Backend-Problem-Details [NIEDRIG]

`GlobalExceptionHandler` setzt `detail`-Strings teils direkt im Java-Code (englisch). Frontend uebersetzt anhand `code`-Feld. Fuer reine API-Konsumenten (Swagger, externe Clients) bleibt der englische Text. Backend hat bereits eine `messages*.properties`-Struktur — `MessageSource`-Lookup ueber `code` waere konsistent.

---

## 4. POSITIVE BEFUNDE — was bereits ausgezeichnet ist

Diese Punkte dienen der Sichtbarmachung, was die Codebasis aktuell richtig macht:

1. **Architektur-Tests (ArchUnit)** vorhanden mit drei klar dokumentierten Regeln — die Regeln selbst sind sehr gut formuliert, nur die Abdeckung koennte breiter sein (siehe 1.4).
2. **Optimistic Locking** via `AbstractEntity.@Version` durchgaengig, dokumentiertes `*WithinTransaction`-Pattern in `JobOrderService` und `JobOrderHandoverService`.
3. **Pessimistic Locking** an den richtigen Stellen ([`InventoryItemRepository:122`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/repository/InventoryItemRepository.java:122) `findByIdForUpdate`, [`JobOrderRepository:35`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/repository/JobOrderRepository.java:35) `lockAllJobOrders` fuer reorder).
4. **PII-Masking** in [`PiiMasker.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/logging/PiiMasker.java) maskiert JWTs, Emails, Bearer-Token — fuer beide Module gleichermassen.
5. **Correlation-ID-Filter** mit Sicherheits-Validierung (`isSafe()`, 128-Zeichen-Limit) gegen Log-Injection.
6. **RFC 7807** sauber zentralisiert in `GlobalExceptionHandler` mit `code` + `correlationId` als Extensions; 4xx ohne Stacktrace, 5xx mit.
7. **`@EntityGraph`** konsistent fuer Eager-Loads ([`MissionRepository:22-23`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/repository/MissionRepository.java:22)).
8. **Flyway-Migrationen** durchnummeriert ohne Luecken (V1-V72), idempotent (V51 `DROP CONSTRAINT IF EXISTS`, V69 `IF NOT EXISTS`), GIN-Indexe via `pg_trgm` in V35.
9. **OAuth2 OIDC** mit Keycloak korrekt eingebunden, JWT-`sub` als stabile User-ID (UUID-Validierung in `UserService:75-84`).
10. **Resilience4j** umfassend angewendet (Retry/CircuitBreaker/Timeout/Bulkhead) im Frontend `BackendApiClient`. Event-Logger fuer State-Transitions.
11. **i18n vollstaendig**: Keine literalen Umlaute mehr in `.properties`-Dateien (Stichprobe `messages_de.properties` keine Treffer fuer Latin-1-Umlaute), Flash-Attributes nutzen i18n-Keys.
12. **Keine `alert()`/`confirm()`/`prompt()` mehr im aktiven Code** — alle verbleibenden Treffer sind Kommentare, die das KRT-Modal-Pattern dokumentieren. Custom `toast.html`-Fragment mit gemeinsamem Confirm-Modal.
13. **Type-safe Configuration** ueber `@ConfigurationProperties + @Validated + @NotBlank/@URL/@Min/@Max` fuer Logging, Keycloak-Sync, Rate-Limit, UEX, Problem-URI, HTTP-Timeouts.
14. **CSP** mit Nonce + `strict-dynamic` fuer `<script>`-Elemente. Sicherheits-Header (X-Frame-Options, Referrer-Policy, Permissions-Policy) vollstaendig.
15. **Bot-Schutz** und **SSO-Re-Auth** mit `prompt=none` und Anti-Endlosschleifen-Cookie.
16. **Versions-Katalog** sauber: `gradle/libs.versions.toml` + `versions.properties` (refreshVersions-Konvention).
17. **CycloneDX-SBOM** fuer beide Module (vs. fruehere ANALYSIS.md: Frontend hatte das nicht — inzwischen behoben).
18. **Docker**: HEALTHCHECK in `backend/Dockerfile` UND `frontend/Dockerfile`, EXPOSE 11261 / 18081 korrekt, non-root User (UID 10001), Layered-JAR.
19. **`open-in-view: false`** + `default_batch_fetch_size: 100` — defensive JPA-Konfiguration.
20. **Sehr ausfuehrliche Test-Suite**: 70+ Tests im flatpackage, 32 Service-Tests, 12 Controller-Tests, 3 Security-Tests, 1 Migration-Test (TC). Timezone-, Concurrency-, Data-Leak-, Access-Control-Tests vorhanden.

---

## 5. ZUSAETZLICHE EMPFEHLUNGEN (ueber CLAUDE.md hinaus)

### 5.1 OpenAPI-Spec im CI verifizieren

[`backend/src/main/resources/api/openapi.json`](backend/src/main/resources/api/openapi.json) sollte aus `MockMvc`/SpringDoc generiert und gegen die committed Version verglichen werden — sonst driftet die Spec von der Implementierung.

### 5.2 Pre-Commit-Hooks

Light-weight Pre-Commit-Hook der prueft:
- `.properties` enthalten keine literalen Umlaute (`grep -P '[À-ſ]' messages*.properties` muss leer sein)
- Keine `alert(`/`confirm(`/`prompt(` Aufrufe (nicht: Kommentare) in `static/js/` und `templates/`
- Keine `LocalDateTime` ausserhalb der erlaubten Klassen

### 5.3 Migration-Test-Coverage

Ein Test der **alle** Flyway-Migrationen gegen leere Postgres-Container fahrt + dann `validate` macht. Heute nur via `ENABLE_TC=true` triggerbar.

### 5.4 Mutation Testing aktivieren

`pitest` ist konfiguriert ([`build.gradle.kts:101-125`](build.gradle.kts:101)), aber nur "on demand". Fuer die Kern-Services (`MissionService`, `JobOrderService`, `PersonalInventoryItemService`) waere ein quartalsweiser Lauf eine kosteneffiziente Methode, um Test-Quality zu validieren.

### 5.5 Stoffe in `keycloak-theme/` dokumentieren

Das `keycloak-theme/`-Verzeichnis ist in der README nicht erklaert — fuer Onboarding-Zwecke ein kurzer Abschnitt: wie wird das Theme beim Container eingebunden, wann muss man `docker compose down -v` machen, etc.

### 5.6 PR-Template / GitHub-Actions

Repo enthaelt `CONTRIBUTING.md`, aber keine Workflow-Dateien (kein `.github/`-Ordner im Root sichtbar). Fuer ein Open-Source-faehiges Projekt waeren GitHub-Actions sinnvoll: `./gradlew check` + `./gradlew test` + Dependency-Check als PR-Gate.

---

## 6. ABGEGLICHEN MIT VORHERIGER `ANALYSIS.md` (2026-05-11)

Diese Findings der vorherigen Analyse sind **inzwischen behoben** und tauchen oben nicht mehr auf:

| Punkt (alt) | Status heute |
|---|---|
| 1.1 Fehlbenannter Test-Ordner `de.greluc.krt.iri.basetool.backend/` | NICHT MEHR VORHANDEN — Verzeichnis existiert nicht |
| 1.2 Tests fehlen fuer Promotion-System (V72) | V72 ist jetzt `add_role_code.sql`, nicht das Promotion-System. Das Promotion-System ist im aktuellen Branch nicht (mehr) Teil des Codes — wahrscheinlich rueckgaengig gemacht oder noch nicht gemerged |
| 2.1 `confirm()`/`alert()` in Templates | BEHOBEN — nur noch Kommentar-Hinweise vorhanden, kein aktiver Code-Aufruf |
| 2.2 Umlaute in `.properties` | BEHOBEN — keine literalen Umlaute in `messages*.properties` mehr nachweisbar |
| 2.3 Hardcodierte englische Strings in `AdminMaterialsPageController` | BEHOBEN (oder Datei entfernt) — keine englischen `errorToast`-Strings mehr |
| 2.5 ~124 `ResponseStatusException` in 20 Dateien | BEHOBEN — nur noch 6 Vorkommen in 2 Dateien (`GlobalExceptionHandler.java`, `BadRequestException.java`) |
| 2.6 Frontend ohne CycloneDX-SBOM | BEHOBEN — [`frontend/build.gradle.kts:91`](frontend/build.gradle.kts:91) hat `cyclonedxBom`-Block |
| 3.1 Dockerfile EXPOSE 10261 | BEHOBEN — `EXPOSE 11261` korrekt |
| 3.2 Hardcodierte JetBrains-Annotations 26.0.2 vs. 26.1.0 | BEHOBEN — beide nutzen `compileOnly("org.jetbrains:annotations:_")` mit zentraler Version 26.1.0 in `versions.properties` |
| 3.3 `tasks.withType<Test>` dupliziert | BEHOBEN — siehe `build.gradle.kts:24-77` `subprojects { plugins.withId(...) }`-Bloecke |
| 3.4 Healthcheck im Dockerfile fehlt | BEHOBEN — beide Dockerfiles haben `HEALTHCHECK` |
| 5.7 ArchUnit-Tests fehlen | BEHOBEN — drei Regeln implementiert (siehe 1.4 fuer Erweiterungsvorschlaege) |

---

## 7. PRIORISIERTE TODO-LISTE (Vorschlag fuer den Nutzer)

Wenn Aenderungen freigegeben werden, schlage ich diese Reihenfolge vor:

| # | Prio | Aufwand | Aufgabe |
|---|---|---|---|
| 1 | HOCH | < 30 min | `RuntimeException` durch `BadRequestException`/`NotFoundException` in `RefineryOrderService` (3x), `KeycloakService` (2x), `JobOrderHandoverReportService` ersetzen (1.2) |
| 2 | HOCH | ~30 min | `SecurityContextHolder`-Verwendungen in 4 Controllern in einen `AuthHelperService` extrahieren (1.1) |
| 3 | HOCH | ~30 min | `MissionMapper`-Resolver-Methoden in den Controller/Service auslagern, Field-Injection entfernen (1.1) |
| 4 | HOCH | ~30 min | ArchUnit-Regeln erweitern (mapper/controller einbeziehen, Write-Methoden brauchen `@PreAuthorize`) (1.4) |
| 5 | MITTEL | ~10 min | OWASP-CVSS-Gate von 11 auf 9 senken (2.1) |
| 6 | MITTEL | ~10 min | `jacoco`-Plugin im `backend/build.gradle.kts` aktivieren (2.5) |
| 7 | MITTEL | ~20 min | docker-compose Resource-Limits ergaenzen (2.6) |
| 8 | MITTEL | ~30 min | `@TimeLimiter` auf POST/PUT/DELETE im `BackendApiClient` ergaenzen (2.4) |
| 9 | HOCH | ~3-4 h | Test-Profil auf Testcontainers/Postgres + Flyway umstellen (1.3) |
| 10 | MITTEL | ~1 h | Inventur und Konsolidierung der Public-permitAll-Endpoints im Frontend (2.3) |
| 11 | MITTEL | ~30 min | Magic-Numbers (`750`, `0.0001`) als Konstanten extrahieren (2.8) |
| 12 | NIEDRIG | ~30 min | Cleanup `build.gradle.kts` Backend (Duplikate), ArchUnit-Version in libs.versions.toml (3.1, 3.2) |
| 13 | NIEDRIG | ~30 min | `Optional.get()`-Patterns durch `orElseThrow` ersetzen (2.9) |
| 14 | NIEDRIG | optional | OpenAPI-Drift-Test, Pre-Commit-Hooks, Migration-Tests aktivieren (5.x) |

---

## 8. RUECKFRAGEN AN DEN NUTZER

Bevor ich konkret aenderungen anstosse, brauche ich Entscheidungen bei:

1. **`RuntimeException` → domaenenspezifisch (1.2):** Sollen die 6 Stellen in einem Stueck behoben werden oder pro Datei einzeln?
2. **`SecurityContextHolder` aus Controllern (1.1):** `AuthHelperService` neu anlegen oder `UserService` erweitern (was bereits einzig erlaubte Stelle waere)?
3. **Test-Profil → Testcontainers (1.3):** Default-Umstellung oder als zweites Profil (`testcontainers`) parallel zu H2 (`unit-fast`)?
4. **`MissionMapper`-Refactoring (1.1):** Resolver-Methoden in `MissionService` verschieben (cleaner) oder im Controller berechnen und dem Mapper ueber `@Context` mitgeben (kompatibler)?
5. **OWASP-Gate (2.1):** Direkt auf 9 oder erst Inventur der aktuellen Findings + dann pragmatisch senken?
6. **Frontend `permitAll()`-Aufraeumung (2.3):** Vollstaendige Inventur und Migration zu `authenticated()` mit Public-Whitelist, oder vorerst nur ArchUnit-Regel haerten (1.4)?

Sobald du die gewuenschten Punkte freigibst, gehe ich sie in der von dir gewuenschten Reihenfolge an.
