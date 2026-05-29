# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Profit Basetool — a squadron-management web app (mission planning, hangar, inventory, refinery, user admin) for the "DAS KARTELL" / IRIDIUM organization. Two Spring Boot 4 modules (`backend`, `frontend`) on Java 25, PostgreSQL 18, Keycloak 26 OAuth2, Redis-backed Spring Sessions. Gradle 9 with Kotlin DSL. Dependencies are managed by [refreshVersions](https://jmfayard.github.io/refreshVersions/) — **edit `versions.properties`, not `build.gradle.kts`**. Run `./gradlew refreshVersions` to discover updates.

## Design system
Befolge das Design-System unter `.claude/skills/das-kartell-design/`.
README.md = Quelle der Wahrheit für Farben, Typo, Komponenten.
Wichtig: Bereichsfarben nach offiziellem Manual benennen
(#A3000A = Sub-Radar, #355DDC = Forschung, #37BBC0 = Raumüberlegenheit).

## Build, run, test

Always use the Gradle wrapper. **Never** use the IDE test runner or the harness `run_test` tool — Gradle is the only sanctioned test path. This is a hard project rule and applies even when iterating on a single test.

```bash
./gradlew :backend:test                                    # backend tests
./gradlew :frontend:test                                   # frontend tests (also produces JaCoCo report)
./gradlew test                                             # all tests
./gradlew :backend:test --tests "FullyQualifiedClassName"  # single test class
./gradlew :backend:test --tests "ClassName.methodName"     # single test method
./gradlew :backend:bootRun                                 # backend on https://localhost:11261 (dev profile)
./gradlew :frontend:bootRun                                # frontend on http://localhost:18081 (dev profile)
./gradlew :backend:cyclonedxBom                            # SBOM into backend/docs/
./gradlew :frontend:cyclonedxBom                           # SBOM into frontend/docs/
./gradlew check                                            # full static analysis: Checkstyle (Google Java Style) + SpotBugs + tests
./gradlew :backend:checkstyleMain :backend:spotbugsMain    # backend lint only
./gradlew :frontend:checkstyleMain :frontend:spotbugsMain  # frontend lint only
```

Tests force `spring.profiles.active=test`; `bootRun` forces `dev`. Both `Test` and `BootRun` set `--enable-native-access=ALL-UNNAMED` and a Mockito agent JVM arg.

## Linting / static analysis

- **Checkstyle** (Google Java Style, `config/checkstyle/google_checks.xml`) and **SpotBugs** (`spotbugsMain`, wired into `check`) run against the `main` source set of both modules. Reports land under `<module>/build/reports/{checkstyle,spotbugs}/main.{html,xml}`.
- **Every new or modified piece of code must be linted before the task is considered done.** Run at least `./gradlew :<module>:checkstyleMain :<module>:spotbugsMain` (or `./gradlew check` for the full sweep) and read the reports.
- **All Checkstyle and SpotBugs errors *and* warnings introduced or touched by your change must be fixed.** Do not silence findings with `@SuppressWarnings`, `@SuppressFBWarnings`, or Checkstyle suppression files unless the rule is genuinely wrong for that specific call site — and in that case leave a one-line comment explaining why.
- Pre-existing findings in code you did not touch are out of scope; do not opportunistically clean them up in an unrelated change. But never *add* a new finding on top of them.
- **Run `./gradlew spotlessApply` locally before every push.** Spotless is wired into `check` via `isEnforceCheck = true`, and Checkstyle runs with `isIgnoreFailures = false` + `maxWarnings = 0` — any unformatted Java file or new Checkstyle warning fails CI immediately.

## Local stack

Use Docker Compose profiles:

```bash
docker compose --profile dev up -d db-backend-dev db-keycloak-dev keycloak-dev redis-dev   # deps only, run apps locally
docker compose --profile dev up -d                                                          # full dev stack with host port exposure
docker compose --profile prod up -d                                                         # prod-equivalent stack behind nginx-proxy-manager
docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml \
    --profile dev up -d                                                                     # isolated test stack with throwaway credentials
```

Host ports (dev profile only): backend `11261`, frontend `18081`, Keycloak `18080`, backend DB `15432`, Keycloak DB `15433`, Redis `6379`, NPM admin `10081`. A `.env` at repo root is required for the regular dev/prod profiles (see README for keys). The isolated test stack instead reads `.env.test` plus a locally generated `keystore.p12` and a stripped `realm-export.json` — see the README's `Running the Local Test Stack` section for setup, and never substitute production artifacts for those.

The backend serves HTTPS with a self-signed cert (`keystore.p12`, password `changeit`); the frontend talks to `https://backend:11261` in prod and `http://localhost:11261` (overridable via `BACKEND_URL`) in dev. Swagger UI is at `https://localhost:11261/swagger-ui.html`.

## Architecture

### Module split
- **`backend`** — REST API only. Layered: `controller` → `service` → `repository` → `model` (JPA entities), with `dto` records, MapStruct `mapper`s, `config` (security, caching, OpenAPI, rate limiting, WebClient), `integration` (UEX external API), `task` (scheduled jobs), `filter`/`interceptor` (correlation ID, deprecation headers), `annotation` (`@ApiDeprecation`).
- **`frontend`** — Thymeleaf server-rendered UI that calls the backend via WebClient. No business logic of its own; `service.BackendApiClient` is the single seam. Persistent state across frontend restarts goes in Redis (Spring Session).

The frontend never talks to PostgreSQL or Keycloak Admin API directly. The backend never serves HTML.

### Security model
- Both modules use Spring Security with Keycloak OIDC. Backend = resource server (validates JWT); frontend = OAuth2 client (browser SSO + bearer-token relay).
- Authorization is centralized in `@PreAuthorize` annotations on services/controllers — keep checks out of business logic. Roles mapped from JWT are prefixed with `ROLE_` and uppercased. The architectural invariants here (no `SecurityContextHolder` outside the auth-helper service, every `@RestController` carries at least one `@PreAuthorize`, controllers do not return JPA entities, frontend does not depend on Spring Data JPA) are enforced as ArchUnit rules in [`backend/.../ArchitectureTest.java`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java) and [`frontend/.../ArchitectureTest.java`](frontend/src/test/java/de/greluc/krt/iri/basetool/frontend/ArchitectureTest.java) — adding a new violation will fail `./gradlew test`.
- Roles: `ADMIN`, `OFFICER`, `LOGISTICIAN`, `MISSION_MANAGER`, `SQUADRON_MEMBER`, `GUEST`. Hierarchy: `ADMIN > LOGISTICIAN`, `ADMIN > MISSION_MANAGER`, `OFFICER > LOGISTICIAN`, `OFFICER > MISSION_MANAGER`. Full matrix in `ROLES_AND_PERMISSIONS.md`.
- `LOGISTICIAN` and `MISSION_MANAGER` are granted contextually via `is_logistician` / `is_mission_manager` flags on `org_unit_membership` rows. A user with the flag set on *any* of their OrgUnit memberships (Staffel or SK) receives the flat authority via `CustomJwtGrantedAuthoritiesConverter`; per-OrgUnit scoping is then enforced by `@PreAuthorize` SpEL against `OwnerScopeService.canEditOrgUnit(...)`. Legacy `app_user.is_logistician` / `is_mission_manager` columns are read as a fallback only for users without any membership row (pre-V98-backfill edge case); they are dropped in the destructive cleanup release.
- Frontend has a `BotProtectionFilter` and `SsoReAuthenticationEntryPoint`: known scanner paths return 404 directly; legitimate paths with expired sessions get a silent `prompt=none` Keycloak redirect.

### Multi-user data isolation (CRITICAL)
- Every read/write must filter by JWT `sub` unless the caller has an elevated role (`ADMIN`, `OFFICER`, …). Enforce this in the service layer, not the controller.
- For unauthenticated guests, return only the minimum required data. Sensitive fields (email, real name, internal orders/items) MUST be explicitly cleared in the controller — use a `cleanupForGuest`-style helper to prevent information disclosure.

### Multi-org-unit tenancy (CRITICAL)
The system supports multiple OrgUnits in parallel — two kinds coexist under a shared `org_unit` table with a `kind` discriminator: `SQUADRON` (the legacy Staffel) and `SPECIAL_COMMAND` (SK, introduced by SPEZIALKOMMANDO_PLAN.md). The IRIDIUM Squadron sits at the canonical UUID `00000000-0000-0000-0000-000000000001`. Every staffel-scoped aggregate carries two FKs during the dual-write soak: the legacy `owning_squadron_id` plus the new `owning_org_unit_id` (kept in lockstep by JPA `@PrePersist`/`@PreUpdate`/`@PostLoad` hooks). Repository queries already read the new column; the legacy column is dropped in the destructive cleanup release.

- **Org-unit scope is enforced in the service layer**, not the controller. Use [`OwnerScopeService.currentOrgUnitId()`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/OwnerScopeService.java) for list-endpoint filters (which now consume a three-parameter `ScopePredicate` tuple: `boolean isAdminAllScope`, `UUID activeOrgUnitId`, `Set<UUID> memberOrgUnitIds`) and `OwnerScopeService.canSee*`/`canEdit*` for `@PreAuthorize` SpEL on detail/write endpoints. Admins without an active pin get all-scope visibility; admins with a pin get the same restrictive view as a member; non-admins see the union of all their memberships unless they pin one specifically.
- **Aggregate scope kinds:**
  - *Strict-staffel* (org-unit-scoped, no cross-staffel escape): `Ship`, `InventoryItem` (direct Lager-View), `RefineryOrder`, `Operation`. Direct CRUD and list endpoints filter by `owning_org_unit_id`; detail endpoints gate on `canSee*`/`canEdit*` from `OwnerScopeService`.
  - *Cross-staffel-with-public-escape*: `Mission`. Visible to other OrgUnits iff `is_internal = false`; editable only by the owning OrgUnit + admins. The repository's `searchMissions` enforces this via the `owning_org_unit.id IN (:memberOrgUnitIds) OR is_internal = false` clause.
  - *Cross-staffel workspace*: `JobOrder` + linked `JobOrderMaterial` + `JobOrderHandover`. **No OrgUnit filter on access** — anyone with the role/permission can read or edit. Job Order carries two OrgUnit refs: `creating_org_unit_id` (immutable, who authored it) and `requesting_org_unit_id` (editable, on whose behalf it runs — accepts any active OrgUnit, Staffel or SK). Inventory items linked to a job order via `job_order_id` surface cross-OrgUnit inside the order's UI even when they belong to a different OrgUnit's stock, but they NEVER leak into a foreign OrgUnit's Lager-View (split repository methods enforce this — `findGlobalByFilters` is gated, `findByJobOrderIdOrdered` is not).
- **At create time, stamp the OrgUnit** on the entity via the central resolver [`OwnerScopeService.resolveSquadronForPickerOutput(targetUser, pickerOutputOrgUnitId)`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/OwnerScopeService.java) — never read `user.getSquadron()` directly. The resolver enforces the SPEZIALKOMMANDO_PLAN.md §5.5.1 matrix: 0 memberships → 400; 1 + no picker output → auto-stamp; 1 + valid picker output → honoured; 1 + foreign picker output → 400; >1 + no picker output → 400 (force explicit choice); >1 + valid picker output → honoured. SK selection currently 400s because the legacy `owning_squadron_id` is still NOT NULL — lifts in the destructive cleanup release. For Job Order, set `creating_org_unit_id` from the caller's context (immutable after) and `requesting_org_unit_id` from the request DTO.
- **Admin area is admin-only** (post-Phase-4 lockdown), with one carve-out. Stammdaten (Cities, Materials, ShipTypes, Locations, UEX, etc.), user-management endpoints (role flags, rank, attribute patches), announcement writes, system settings, and **SK lifecycle** (`/admin/special-commands` create / rename / delete) are all `@PreAuthorize("hasRole('ADMIN')")`. SK **member management** (add / remove / flag-toggle on `is_logistician` / `is_mission_manager`) is open to ADMIN or to a user with `is_lead = true` on that specific SK — gated by `SpecialCommandSecurityService.canManageMembers(scId, authentication)`. The Lead-toggle endpoint itself stays ADMIN-only so a Lead cannot self-escalate. The **promotion-system maintenance** (Themenbereiche / Bewertungsverwaltung / Rangvoraussetzungen) is re-opened to OFFICER under an org-unit-scope gate: an Officer of Squadron X may manage X's criteria via `canEditSquadron(topic.owningSquadron.id)`. Admins can additionally **toggle the entire promotion subsystem per Squadron** via `PATCH /api/v1/squadrons/{id}/promotion-enabled` (page: `/admin/settings`) — `OwnerScopeService.isPromotionFeatureEnabledForCurrentScope()` short-circuits every promotion service to empty / 403 when the flag is OFF on the caller's effective squadron. **Admin pin awareness**: an admin without an active pin (all-scopes mode) keeps the promotion menu visible so they can re-enable a locked-out squadron; an admin pinned to a squadron honours that squadron's flag so the pinned view matches what a member would see. To re-enable a locked-out squadron after pinning it, clear the pin (back to all-scopes) or navigate directly to `/admin/settings`, which is not gated by this check. **SKs can never participate in the promotion subsystem** — the V97 CHECK `kind = 'SQUADRON' OR is_promotion_enabled = FALSE` and the V101 trigger `guard_promotion_topic_owner_kind` enforce this at the DB layer; the `SpecialCommand` JPA entity constructor + setter override block it at the app layer; ArchUnit rule `promotionTopicOwningSquadronMustStayTypedSquadronNotOrgUnit` blocks it at the type layer.
- **ArchUnit guard `staffelScopedServicesMustWireOwnerScopeOrAuthHelper`** in [`ArchitectureTest`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java) breaks the build if a service from the staffel-scoped whitelist stops injecting `AuthHelperService` / `OwnerScopeService`. Update the whitelist when you add a new staffel-scoped aggregate. The companion rule `noNewJoinColumnReferencingSquadronIdOutsideGrandfatheredEntities` prevents re-introducing the legacy `@JoinColumn(name = "squadron_id")` on a new entity (only `User.squadron` + `MissionParticipant.squadron` are grandfathered).
- **MDC field `orgUnitId`** is emitted by [`CorrelationIdFilter`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/logging/CorrelationIdFilter.java) on every request alongside `correlationId` and `userId`; the legacy `squadronId` field is emitted in parallel for one release for log-pipeline migration. Logback patterns must include `%X{orgUnitId}` to keep audit trails intact.
- **Active-context relay**: the frontend sends both `X-Active-Org-Unit-Id` (canonical) and the legacy `X-Active-Squadron-Id` header on every outbound WebClient call; the backend reads the new name first and falls back to the legacy. The session attribute (Redis-backed) is keyed at `iridium.activeOrgUnitId` with the legacy `iridium.activeSquadronId` mirrored for one release. Both aliases drop in the destructive cleanup release.

### Database
- Schema is owned by Flyway: every change is a new `V<n>__<description>.sql` in `backend/src/main/resources/db/migration`. **Hibernate `ddl-auto` is `validate` everywhere — never set it to `update` or `create`.** Full conventions (destructive-ops two-phase rule, data-migration patterns, performance/locking, test caveats, pre-merge checklist) live in [`backend/src/main/resources/db/migration/README.md`](backend/src/main/resources/db/migration/README.md) — read that before adding a migration.
- `DataInitializer` seeds roles/permissions on startup.
- Avoid N+1: prefer `JOIN FETCH`, `@EntityGraph`, or Spring Data projections.

### Concurrency — read this before touching multi-step transactions
The codebase has been bitten by optimistic-locking traps several times. The rules below exist because of real bugs that shipped.

- **Optimistic locking via `@Version`** — every write DTO carries the `version` field; the frontend echoes it back; concurrent modifications surface as `ObjectOptimisticLockingFailureException` → HTTP 409. Don't strip the version from DTOs to "make it simpler."
- **Frontend DOM version sync** — when an entity is updated via AJAX (dropdown change, row reorder, etc.), the new `version` must propagate to **every** related DOM element in the same context (edit/action buttons, modals inside the same `<tr>` or container). A missed `data-version` attribute → 409 on the user's next click. If targeted updates are too tangled, just `window.location.reload()` on success.
- **Pessimistic locking for bulk reorders** — use `@Lock(LockModeType.PESSIMISTIC_WRITE)` (or atomic SQL) for priority shifts and reorder operations to avoid races.
- **Intra-transaction service calls — `…WithinTransaction` pattern.** When a `@Transactional` service method modifies an entity (directly or via cascaded `repository.save()`) and then calls another service that operates on the **same entity**, the inner method's own `findById()` + `save()` + `flush()` will collide with the already-incremented `@Version` field, causing 409. Fix: expose a dedicated `completeSomethingWithinTransaction(Entity entity)` method annotated `@Transactional(propagation = MANDATORY)` that operates on the already-managed entity and relies on dirty-checking — no `save()`/`flush()` of its own. Canonical example: `JobOrderService.completeJobOrderWithinTransaction()`. Apply this consistently to handover, booking, transfer, and any similar flow.
- **Bulk updates inside loops.** A `@Modifying` repository query with `clearAutomatically = true` (e.g. `unlinkJobOrderMaterial`) detaches the **entire** persistence context — including all sibling entities of the aggregate currently being processed. NEVER execute such a bulk update inside a loop that mutates more than one item of the same aggregate (e.g. multiple `JobOrderMaterial`s of the same `JobOrder`): subsequent iterations will operate on detached entities, and any `repository.save(entity)` call on a detached entity silently does `EntityManager.merge()`, producing a second `@Version` bump on rows already updated in the same transaction → 409. The fix (extension of the `*WithinTransaction` pattern, see `JobOrderHandoverService.createHandover()`):
  1. Inside the loop, mutate only managed entities and rely on Hibernate dirty-checking — do NOT call `repository.save(child)` explicitly.
  2. Collect the IDs of items that need a clearing bulk update in a `Set<UUID>` and run those bulk updates exactly once **after** the loop AND **after** persisting any new aggregate root.
  3. If the completion check needs the freshly persisted state, re-fetch the aggregate root once via `findById(id)` to get a managed instance with up-to-date `@Version`, then hand it to the dedicated `…WithinTransaction(...)` method.
  Apply this rule to every bulk-update + multi-item flow (handover, booking, refinery, transfer).

### API conventions
- **Versioned URI paths**: `/api/v1/...`. Breaking changes → new version (`/api/v2/...`). Use `@ApiDeprecation(sunset = "YYYY-MM-DD", replacement = "/api/v2/...")` on retired endpoints; `DeprecationInterceptor` emits `Deprecation` / `Sunset` / `Link` headers automatically and `OpenApiDeprecationConfig` reflects it in the OpenAPI spec.
- **DTOs only at boundaries.** Never expose JPA entities at controller boundaries. DTOs are records. All write DTOs carry Jakarta validation annotations (`@NotBlank`, `@NotNull`, `@Min`, `@Max`, …). Use a MapStruct mapper (`@Mapper(componentModel = "spring")`) for Entity↔DTO conversion; break circular refs with `@Mapping(ignore = true)`.
- **`@Valid`** on every `@RequestBody` for write operations (POST/PUT/PATCH).
- **Errors** — RFC 7807 `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`. Validation errors add an `errors` object (field → message). Extend `GlobalExceptionHandler` rather than throwing into the void; problem-type URIs come from `AppProblemProperties`, not hardcoded strings. Document the format in OpenAPI; keep frontend error display in sync.
- **Pagination & sorting** — all list endpoints take Spring's `Pageable` and return a `PageResponse` wrapper (total elements, pages, current page). **Whitelist allowed sort fields** in the service — never pass user input directly to `Sort` (unstable sorting + information disclosure risk).
- **All times in UTC** — store/process as `Instant` or `OffsetDateTime`. Convert to the user's local timezone in the display layer only. Write serialization tests for timezone behavior.
- **OpenAPI docs** — every REST endpoint must carry SpringDoc annotations (`@Operation`, `@ApiResponses`). Keep `backend/src/main/resources/api/openapi.json` in sync with controller changes.

### Frontend resilience & config
- **WebClient** is centrally configured (base URL, default headers, connect/read/write timeouts).
- **Resilience4j** wraps every backend call (Timeout, Retry, CircuitBreaker, Bulkhead). State transitions are logged via `ResilienceEventLogger` so `SERVICE_UNAVAILABLE` / `BACKEND_TIMEOUT` always have a matching log line.
- **Reactor context propagation is mandatory for any new `ThreadLocal` you want to see inside `WebClient` exchange filters.** `WebClient.exchange()` runs on a Reactor-Netty worker thread, not the servlet thread; classic `ThreadLocal` values are not copied across threads. Register a `ThreadLocalAccessor` on `ContextRegistry.getInstance()` in [`ReactorContextPropagationConfig`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/config/ReactorContextPropagationConfig.java) (which also enables `Hooks.enableAutomaticContextPropagation()` at startup). The existing accessors cover `ActiveSquadronContext` (active-OrgUnit pin → `X-Active-Org-Unit-Id` outbound header) and `CorrelationContext` (correlation id propagation). Forgetting the accessor means the holder is invisible on the worker thread and the outbound call silently drops whatever it carried.
- Use `MockWebServer` / WireMock to test error paths.
- **Type-safe configuration** — relevant `application-*.yml` settings live in `@ConfigurationProperties` classes with `@Validated` (Keycloak URIs, backend URLs, limits). Constraints: `@NotBlank`, `@URL`, `@Min`/`@Max`. Test misconfiguration during startup (`test` profile). See `*Properties` classes under `config/`.

### Logging
- Both modules emit one access-log line per request and enrich every log line with MDC fields:
  - `correlationId` — from inbound `X-Correlation-Id` header (configurable via `APP_LOGGING_CORRELATION_ID_HEADER`) or generated UUID; echoed in the response header. The frontend's `WebClientLoggingFilter` propagates the same id to outbound backend calls so both modules' logs share one id per user interaction.
  - `userId` — JWT `sub`, or `anonymous`.
- In `prod`, a `LogstashEncoder` JSON appender writes `logs/{backend,frontend}.json`; errors split into `*-error.log` for fast triage. Configurable via `APP_LOGGING_*` env vars.
- **Never log names, emails, or tokens.**

## Frontend / UI rules

The app follows the "DAS KARTELL" Corporate Design Manual strictly (see `Styleguide.md`):
- **Primary brand color** `#E77E23` (orange). The logo only appears in this orange, white, or black.
- **Backgrounds**: `#000000` / `#141414` (dark mode aesthetic).
- **Headlines**: `Ethnocentric`, **uppercase only**, optical kerning (the font has irregular kerning — apply letter-spacing tweaks for readability).
- **Body**: `Lato` (Light 300 standard, Bold 700 emphasis), clean sans-serif fallback.
- **Visual style**: sci-fi / space organization / technical HUD; geometric shapes (rings, triangles), thin technical markers for content containers.
- **Department colors are semantic** — use them for the right context (Combat red `#A3000A`, Sub-Radar/Covert blue `#355DDC`, Research cyan `#37BBC0`, Profit green `#239E33`, Search & Rescue yellow `#FFD23F`, Marine Corps purple `#7A5E96`).
- **Never** use `confirm()`, `alert()`, or any native browser dialog. Build custom KRT-styled modals/toasts.

### Responsive design (mandatory)
Every layout change and new component must work across **four** device classes:
- **Smartphone** (≤768px) and **Tablet** (768–1024px) — touch first. "Fat-finger" optimization: minimum click target 44px. Collapse multi-column grids to single-column; let wide tables scroll horizontally.
- **Desktop** (1024–1600px) and **Ultra-wide** (1600px+) — exploit the space (permanently docked sidebars, auto-fit grids for cards/dashboards) but cap long-form text width (`max-width: 80ch` on `<p>`) for readability.

## i18n

- **Every** user-visible string comes from `messages.properties` (`messages_de.properties` / `messages_en.properties`). No exceptions — labels, buttons, tooltips, error messages, flash messages, alerts, placeholders, titles. No hardcoded text in HTML, JS, or Java. Translation keys for the personal-inventory feature live under `personalInventory.*` and `admin.personalInventory.*`.
- **In `.properties` files**, German umlauts (`ä ö ü Ä Ö Ü ß`) MUST be encoded as `\uXXXX` (e.g. `ä`).
- **In Markdown files** (`CHANGELOG.md`, `README.md`, …), German umlauts MUST be literal UTF-8 characters. Never use `\uXXXX` outside `.properties`.

## Testing

- Tests live in the same package structure under `src/test/java/` mirroring `src/main/java/`.
- Naming: `*Test` suffix (e.g., `UserServiceTest`).
- Structure: Given/When/Then (or Arrange/Act/Assert).
- Mock external/complex dependencies with Mockito (`@Mock`, `@InjectMocks`).
- **Every new feature ships with tests.** No exceptions.
- **Never use production / real credentials in tests or local test stacks.** This is a hard rule. It applies to every kind of test (Mockito unit tests, MockMvc, `@SpringBootTest`, TestContainers integration tests) and to every manually-started local stack used to verify a change. Forbidden inputs include — non-exhaustively — the production `.env` at the repo root, the shared `keystore.p12` at `backend/src/main/resources/keystore.p12`, the shared `realm-export.json` Keycloak dump, real OIDC client secrets, real database passwords, real SMTP credentials, real Keycloak admin passwords, real JWT signing keys. Use dedicated test artifacts instead: `.env.test` (gitignored via `.env.*`), a `keystore.p12` generated locally with a throwaway password, a stripped `realm-export.json` with rotated client secrets and a synthetic test user, the `docker-compose.test.yml` override. The README has a `Running the Local Test Stack` section with the exact `keytool` / Python-rewrite commands. Reason: anything that enters a worktree, a CI log, a container volume, a screenshot artifact, an MCP-preview snapshot or an editor backup has to be assumed leaked and rotated — and the recovery is cheaper if it never had to happen in the first place. When you spin a local stack up to verify a UI change, always source `.env.test` (never `.env`), point Docker Compose at `--env-file .env.test`, and tear the stack down with `down --volumes` after the verification.

Minimal example:

```java
package de.greluc.krt.iri.basetool.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidelinesExampleTest {
    @Test
    void shouldPassExampleTest() {
        // Given
        boolean condition = true;
        // When
        // Execute the method under test
        // Then
        assertTrue(condition, "demonstration test");
    }
}
```

## Java conventions

- **Constructor injection only** (favor Lombok `@RequiredArgsConstructor`). No field `@Autowired`.
- **Records** for DTOs and immutable config wrappers.
- **Modern Java**: switch expressions, pattern matching (`instanceof`, `switch`), sealed classes where they help with exhaustiveness.
- **Lombok** — maximize it (`@Slf4j`, `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Data`) to avoid boilerplate.
- **JetBrains annotations** (`@NotNull`, `@Nullable`, `@Contract`) wherever they communicate a real contract.
- **Logging**: `@Slf4j` — never instantiate loggers manually.

## Documentation

- **Maintain `CHANGELOG.md`** for every user-visible change (features, fixes, env-var additions). No exceptions.
- **CHANGELOG entries must be short, terse and to the point — only the essentials.** One to three sentences per bullet covering *what* changed and *why it matters to the user*. No multi-paragraph design rationales, no exhaustive file lists, no copy-pasted commit messages, no architectural reasoning that belongs in the PR description or Javadoc. Mention the area affected (controller / migration / config) and the user-visible effect — anything beyond that is noise. If a bullet grows past ~3 sentences, cut it.
- Keep `README.md` current when architecture or env vars change.
- **Javadoc is mandatory** on every class, interface, enum, record, and public/protected method — no exceptions, including trivial getters/setters and Lombok-generated members documented at the field level. Javadoc must describe the *actual* behavior, parameters, return values, side effects, thrown exceptions, and non-obvious invariants of the specific code it annotates. **Generic boilerplate is forbidden** — phrases like "Gets the value", "Returns the result", "Does something", "Helper method", or restating the method name in prose are not acceptable. If you cannot write a concrete, code-specific sentence, read the implementation again until you can.
- **Javadoc is gate-enforced.** Missing Javadoc on a new `public`/`protected` member fails the build via Checkstyle's `MissingJavadocType` / `MissingJavadocMethod` checks — there is no warn-only grace period. Same gate covers summary period (`SummaryJavadoc`), placement between annotations and the declaration (`InvalidJavadocPosition`), `<p>` after blank lines (`JavadocParagraph`), and `@param`/`@return`/`@throws` order (`AtclauseOrder`).

## Git

Do not run destructive Git commands without explicit user instruction: `git reset --hard`, `git clean -fd`, `git push --force[-with-lease]`, `git rebase` on shared/remote branches, `git branch -D`, `git tag -d`, `git stash drop`, or anything that rewrites/discards commits or remote history. Read-only and additive operations (`status`, `log`, `diff`, `add`, `commit`, non-force `push`) are fine when the task needs them.

**Every commit MUST carry a DCO `Signed-off-by:` trailer — always use `git commit -s` (or `-S -s` when GPG-signing).** No exceptions, including AI-generated commits. The trailer's `Name <email>` must match the commit's author identity case-insensitively on the email; the [`.github/workflows/dco.yml`](.github/workflows/dco.yml) check rejects any PR commit lacking a matching sign-off. Bot exemptions (Dependabot / Renovate / GitHub Actions) do NOT apply to commits authored under a real user identity, even if Claude generated the body. If you forget the `-s` flag and the commit is still local (not yet pushed), fix it before pushing: `git commit --amend --signoff --no-edit` for the last commit, `git rebase --signoff main` for the whole branch. For already-pushed commits, ask the user before force-pushing the rewrite — `git push --force-with-lease` falls under the destructive-ops rule above. Full policy and the DCO 1.1 text: [`CONTRIBUTING.md → Developer Certificate of Origin (DCO) sign-off`](CONTRIBUTING.md#developer-certificate-of-origin-dco-sign-off).

**Every commit Claude authors MUST include a `Co-Authored-By:` trailer naming the model — no exceptions, no inconsistency.** This is a transparency requirement that is independent of the DCO sign-off: `Signed-off-by:` attests the human contributor's legal grant; `Co-Authored-By:` discloses AI involvement so reviewers, auditors, and future archaeologists can see which commits had AI in the loop. Use the exact form below, with the human-readable model identifier from this session's system prompt:

```
Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

If the model identifier in your system prompt is different (e.g. `Claude Sonnet 4.6`, `Claude Haiku 4.5`), substitute that — the rule is "name the model that actually wrote the commit", not "always write Opus 4.7". The email stays `noreply@anthropic.com` regardless of model. Apply this rule to **every** commit Claude composes the message for or makes substantive edits to, including one-line typo fixes, CHANGELOG-only commits, and commits where the diff originated from a verbatim user instruction. The bar is "Claude touched this commit", not "Claude wrote most of the diff". If Claude is only running `git` commands the user typed and not authoring anything, the trailer is not required — but when in doubt, include it. Forgetting the trailer is fixed the same way as forgetting `-s`: `git commit --amend --no-edit` (after appending the trailer to the message) for the last commit, locally only, before pushing.

**Always write Git and GitHub content in English — no exceptions.** This covers every piece of text you author for Git or GitHub, regardless of the language the user speaks to you in: commit messages, branch names, tag names and tag messages, PR titles and bodies, PR review comments, issue titles and bodies, issue comments, GitHub Discussions posts, release notes, and any inline comment you write on someone else's behalf via `gh`. If the user prompts you in German (or any other language), translate the substance into English before committing or posting. The only exception is verbatim quoting of existing non-English content (e.g. quoting a user-reported error message in an issue) — the surrounding prose you author stays English.
