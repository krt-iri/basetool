# Development Guidelines for IRIDIUM Basetool

These guidelines are meant to assist advanced developers contributing to the IRIDIUM Basetool project (Squadron management tool for "DAS KARTELL").

## 1. Build and Configuration Instructions

The project consists of two Spring Boot modules: `backend` and `frontend`.
- **Java Version:** 25
- **Spring Boot Version:** 4.0.5
- **Build Tool:** Gradle 9.4.0 (Kotlin DSL)
- **Dependency Management:** Managed via `refreshVersions`. Do not manually edit dependency versions in `build.gradle.kts`; instead, update them in the `versions.properties` file.

### Local Development Setup

To run the application locally without full Docker containerization, use the `dev` profile (default):

1.  **Database Startup:**
    Start the PostgreSQL instance via Docker Compose:
    ```bash
    docker-compose up -d db
    ```
    - Database Name: `krt_basetool`
    - Credentials: `krt_user` / `krt_password` (Port: `5432`),

2.  **Keycloak (Authentication):**
    Ensure access to a Keycloak server. The default `KEYCLOAK_ISSUER_URI` is `https://keycloak.iri-base.org/realms/iri`. If running locally, you must override this environment variable if required.

3.  **Run Backend:**
    ```bash
    ./gradlew :backend:bootRun
    ```
    *(Backend will be accessible at http://localhost:10261)*

4.  **Run Frontend:**
    Ensure `BACKEND_URL` points to the backend (default: `http://localhost:10261`).
    ```bash
    ./gradlew :frontend:bootRun
    ```
    *(Frontend will be accessible at http://localhost:8080)*

## 2. Testing Information

### Configuring and Running Tests
Both modules use JUnit and Spring Boot Test for the testing infrastructure.
- **CRITICAL JUNIE RULE - GRADLE FOR TESTS ONLY:** Junie must actively ensure: ONLY USE GRADLE TO EXECUTE TESTS! (e.g., `./gradlew test`). Under no circumstances use internal IDE test runners or other execution methods. This explicitly includes the `run_test` agent tool — it is strictly forbidden to use `run_test` under any circumstances. Always use the `bash` tool with the appropriate Gradle command instead.
- Tests must be executed using Gradle:
  - Backend tests: `./gradlew :backend:test`
  - Frontend tests: `./gradlew :frontend:test`
  - All tests: `./gradlew test`

### Guidelines on Adding and Executing New Tests
- **CRITICAL JUNIE RULE - MANDATORY TESTS FOR NEW FEATURES:** Junie must actively ensure that for every new functionality, corresponding tests MUST be written without exception.
- **Location:** Put tests in the same package structure under `src/test/java/` corresponding to the tested class in `src/main/java/`.
- **Naming Convention:** Use the `*Test` suffix for test classes (e.g., `UserServiceTest`).
- **Structure:** Use the Given/When/Then pattern (or Arrange/Act/Assert) to cleanly structure tests.
- **Dependencies:** Mock complex or external dependencies (e.g., using `@Mock` and `@InjectMocks` with Mockito).

### Simple Test Example
Below is a simple test class demonstrating the recommended basic structure:

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
        assertTrue(condition, "This is a demonstration test for guidelines.");
    }
}
```

### Use gradle to execute tests
To run all the tests, use the following command in the terminal:
```bash
./gradlew test
```

## 3. Additional Development Information

### Code Style and Corporate Design
The application adheres strictly to the "DAS KARTELL" Corporate Design Manual:
- **Primary Brand Color:** `#E77E23` (Orange).
- **Backgrounds:** Dark Mode Aesthetic (Predominantly Black `#000000` or Dark Gray `#141414`).
- **Typography:** 
  - Headlines: `Ethnocentric` (Uppercase/All-Caps ONLY, apply optical kerning).
  - Body: `Lato` (Light 300 for standard, Bold 700 for emphasis).
- **Visual Aesthetic:** Sci-Fi, Space Organization, Technical HUD Interface with geometric shapes (rings, triangles) and thin technical markers.
- **UI Components:** Do not use native browser/external windows (e.g., `confirm()` or `alert()` dialogs). All UI elements, including confirmation dialogues, must be built using KRT-styled components (e.g., custom modals or toasts) in accordance with the styleguide.

### Responsive Design & Device Classes
- **CRITICAL JUNIE RULE - RESPONSIVE DESIGN:** Junie must actively ensure that all frontend layout changes and new UI components are fully responsive and optimized for four distinct device classes: Smartphone (up to 768px), Tablet (768px - 1024px), Desktop (1024px - 1400px/1600px), and Ultra Wide Desktop (1600px+).
- **Mobile & Touch (Smartphone & Tablet):** Always consider touch interactions ("Fat-Finger" optimization, minimum click target height of 44px). Use CSS media queries to adapt layouts (e.g., converting multi-column grids to single-column, enabling horizontal scrolling for tables).
- **Desktop & Ultra Wide Desktop:** Utilize available space efficiently on large screens (e.g., permanently docked sidebars, auto-fit CSS grids for cards/dashboards) while restricting text line lengths (e.g., `max-width: 80ch` on `p` tags) for readability.

## 4. Best Practices for Software and Libraries

### Java & Spring Boot
- **Immutability:** Use `record` classes for DTOs (Data Transfer Objects) and immutable configuration wrappers.
- **Dependency Injection:** Use constructor injection instead of `@Autowired` field injection for better testability and final fields. Favor Lombok's `@RequiredArgsConstructor`.
- **Modern Java Features:** Utilize `switch` expressions, pattern matching (`instanceof` and `switch`), and sealed classes where applicable to ensure type safety and exhaustiveness.
- **Logging:** Use Lombok's `@Slf4j` annotation for logging instead of manual logger instantiations.
- **Lombok:** Maximize the use of Lombok annotations (e.g., `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Data`) to avoid boilerplate code wherever applicable.
- **JetBrains Annotations:** Utilize JetBrains Annotations (e.g., `@NotNull`, `@Nullable`, `@Contract`) everywhere possible to explicitly state nullability contracts, prevent `NullPointerException`s and reduce boilerplate null-checks.

### API Design
- **Consistent Date/Time/Zone Handling:** All data fields storing time and/or date must always be processed and stored in UTC (e.g., using `Instant` or `OffsetDateTime`). The frontend display must always be converted to the user's local timezone. Document timezone conversion for the display layer and write serialization tests.
- **Pagination & Sorting:** All endpoints that return lists or collections of data MUST implement server-side pagination and sorting using Spring Data's `Pageable`. Use a wrapper like `PageResponse` to include metadata (total elements, pages, current page) and validate sorting fields against an explicit whitelist to prevent unstable sorting or information disclosure.
- **CRITICAL JUNIE RULE - USE DTOs ONLY:** Junie must actively ensure that ONLY DTO `record` classes are used at all API interfaces (Controllers/Services). Never leak direct JPA database entities to the outside. Create missing DTOs and use a central mapper (e.g., MapStruct) for Entity↔DTO conversion. Ensure that no circular references occur when defining DTOs or mappings (e.g., use `@Mapping(ignore = true)` appropriately to break loops). Always define mappers as Spring components (`@Mapper(componentModel = "spring")`). ALL request DTOs for write operations must have appropriate Jakarta-Constraints (e.g., `@NotBlank`, `@NotNull`, `@Min`, `@Max`) in the records.
- **Validation & Error Handling:** Ensure validation using `jakarta.validation` in Request DTOs and consistent error responses (RFC7807 / Problem Details).
  - Use a consistent error format based on RFC7807 (`application/problem+json`).
  - Controllers MUST use the `@Valid` annotation for all RequestBody parameters that involve DTOs for write operations (POST, PUT, PATCH).
  - Extend the global exception handling (`GlobalExceptionHandler`) with Problem responses, including `type`, `title`, `status`, `detail`, `instance`.
  - Document the format in OpenAPI and adapt frontend error display.
  - Avoid hardcoding URLs (e.g., for problem types) in `GlobalExceptionHandler`; use central properties instead.

### Localization & Internationalization (i18n)
- **CRITICAL JUNIE RULE - DYNAMIC TRANSLATION ONLY:** Junie must actively ensure across the entire project that absolutely every text the user gets to see (labels, buttons, tooltips, error messages, flash messages, alerts, etc.) is part of the dynamic translation via `messages.properties`. No exceptions. Never insert or leave hardcoded strings in HTML templates, JavaScript, or Java controllers.
- **UTF-8 Encoding for German Umlauts:** German umlauts (ä, ö, ü, Ä, Ö, Ü, ß) in the translation files (`.properties`) must always use their Unicode UTF-8 codes (e.g., `\u00e4` for `ä`) to guarantee correct display in the frontend.
- **No Hardcoded Strings:** All texts visible to the user (including error messages, placeholders, titles, or similar) must be translated via the message system (translation files) and must not be hardcoded in the code (HTML/Java).

### Database (PostgreSQL)
- **Performance:** Avoid N+1 query issues by utilizing `JOIN FETCH`, `@EntityGraph`, or Spring Data Projections when fetching entities with relationships.
- **Migrations:** All schema changes MUST be managed exclusively via Flyway. Hibernate's `ddl-auto` must always be set to `validate` (or `none`) in all environments. Never use `update` or `create`. Always create a new Flyway migration script (e.g., `V<version>__<description>.sql`) in `backend/src/main/resources/db/migration` for any database schema changes.

### Multi-User Concurrency & Data Isolation
- **CRITICAL JUNIE RULE - MULTI-USER DATA ISOLATION:** Junie must actively ensure that users can only see and modify their own data unless they have elevated privileges (e.g., `ADMIN`, `OFFICER`). Controller and Service layers must rigorously enforce this using token subjects (JWT) and Role-Based Access Control (`@PreAuthorize`).
  - Filter database queries in the service layer by the `sub` (User ID) from the JWT.
  - For unauthenticated users (guests), only the minimum required data may be returned. Sensible fields (e.g., email, real name, internal orders/items) MUST be explicitly cleared or filtered in the controller (e.g., using a `cleanupForGuest` method) to prevent information disclosure.
- **CRITICAL JUNIE RULE - CONCURRENCY AND OPTIMISTIC LOCKING:** To prevent "Lost Updates" in a multi-user environment, all DTOs used for updating existing entities MUST include the `version` field from the database entity. Frontend requests must transmit this `version` to trigger Spring Data JPA's Optimistic Locking (`ObjectOptimisticLockingFailureException` -> `409 Conflict`) upon concurrent modifications.
- **CRITICAL JUNIE RULE - FRONTEND DOM VERSION SYNC:** When updating entities via asynchronous AJAX requests in the frontend (e.g., changing a dropdown, reordering rows), Junie must actively ensure that the returned or incremented `version` is synchronized across ALL relevant UI elements in the same DOM context (e.g., edit buttons, action buttons, modals within the same `tr` or container). Failure to update all related `data-version` attributes will cause 409 Conflict errors on subsequent user actions. If targeted DOM updates are too complex, trigger a full page reload (`window.location.reload()`) upon success.
- **Pessimistic Locking for Bulk Updates:** Race conditions in critical bulk update operations (like shifting priorities or reordering elements) must be avoided by using explicit table or row locks (e.g., `@Lock(LockModeType.PESSIMISTIC_WRITE)` in JPA) or safe atomic database operations.
- **CRITICAL JUNIE RULE - INTRA-TRANSACTION SERVICE CALLS (Optimistic Locking):** When a `@Transactional` service method modifies an entity (directly or via cascade through `repository.save()`) and subsequently calls another service method that operates on the **same entity**, a double-save conflict can occur: the inner method's own `findById()` + `save()` + `flush()` will collide with the already-incremented `@Version` field, causing an `ObjectOptimisticLockingFailureException` (HTTP 409). To prevent this: implement a dedicated `completeSomethingWithinTransaction(Entity entity)` method annotated with `@Transactional(propagation = MANDATORY)` that operates directly on the already-managed entity without its own `save()`/`flush()`. This pattern was established in `JobOrderService.completeJobOrderWithinTransaction()` to fix the handover bug. Apply it consistently to any future handover, booking, or transfer flow.
- **CRITICAL JUNIE RULE - BULK UPDATES INSIDE LOOPS (Optimistic Locking):** A `@Modifying` repository query with `clearAutomatically = true` (e.g. `unlinkJobOrderMaterial`) detaches the **entire** persistence context — including all sibling entities of the aggregate currently being processed. NEVER execute such a bulk update inside a loop that mutates more than one item of the same aggregate (e.g. multiple `JobOrderMaterial`s of the same `JobOrder`): subsequent iterations will operate on detached entities, and any `repository.save(entity)` call on a detached entity silently performs `EntityManager.merge()`, which produces a second `@Version` bump on rows that have already been updated in the same transaction → `ObjectOptimisticLockingFailureException` (HTTP 409). The structural fix (extension of the `*WithinTransaction` pattern, see `JobOrderHandoverService.createHandover()`): (1) inside the loop, mutate only managed entities and rely on Hibernate dirty-checking — do NOT call `someAggregateRepository.save(child)` explicitly; (2) collect the IDs of items that need a clearing bulk update in a `Set<UUID>` and run those bulk updates exactly once **after** the loop AND **after** persisting any new aggregate root; (3) if the completion check needs the freshly persisted state, re-fetch the aggregate root once via `findById(id)` to obtain a managed instance with up-to-date `@Version`, then hand it to the dedicated `completeSomethingWithinTransaction(...)` method. Apply this rule to every bulk-update + multi-item flow (handover, booking, refinery, transfer).

### Security (Keycloak / Spring Security)
- **Role-Based Access Control:** Handle authorization centrally via Method Security annotations (e.g., `@PreAuthorize("hasRole('ADMIN')")`). Keep logic out of controllers. Roles mapped from JWT are prefixed with `ROLE_` and converted to uppercase.

### Git Usage
- **CRITICAL JUNIE RULE - NO DESTRUCTIVE GIT COMMANDS WITHOUT EXPLICIT USER REQUEST:** Junie must never execute destructive or irreversible Git commands unless the user explicitly requests them. This includes, but is not limited to: `git reset --hard`, `git clean -fd`, `git push --force` (or `--force-with-lease`), `git rebase` (on shared/remote branches), `git branch -D`, `git tag -d`, `git stash drop`, and any command that rewrites, deletes, or discards commits, working-tree changes, or remote history. Safe, read-only or additive Git operations (e.g., `git status`, `git log`, `git diff`, `git add`, `git commit`, `git push` without force) may be used freely when required by the task.

## 5. Documentation Guidelines

- **CRITICAL JUNIE RULE - MAINTAIN CHANGELOG:** Junie must actively ensure that all changes (features, bug fixes, adjustments) are documented in the `CHANGELOG.md` without exception.
- **CRITICAL JUNIE RULE - CHANGELOG UMLAUTS:** In `CHANGELOG.md` (and other Markdown documentation files), German umlauts (ä, ö, ü, Ä, Ö, Ü, ß) MUST be written directly as UTF-8 characters. NEVER use Unicode escape sequences like `\u00e4` in Markdown files. The `\uXXXX` rule applies exclusively to Java `.properties` translation files.
- **Project Documentation:** The `README.md` and `CHANGELOG.md` must be kept up-to-date with new architecture decisions, features, and environment variable requirements.
- **API Documentation:** 
  - All REST endpoints must be documented using SpringDoc OpenAPI annotations (e.g., `@Operation`, `@ApiResponses`).
  - Keep Swagger UI (`http://localhost:10261/swagger-ui.html`) synchronized with code changes by updating endpoint models and parameter descriptions.
  - Keep Swagger openapi.json synchronized with code changes by updating endpoint models and parameter descriptions.
- **Code Comments:** Use Javadoc for complex business logic to document *why* a particular approach was chosen, rather than just *what* the code does. Standard boilerplate getters/setters or obvious implementations don't need Javadoc.

### Configuration Properties & HTTP Client Resilience
- **Type-safe Configuration:** Migrate relevant `application-*.yml` settings to type-safe `@ConfigurationProperties` classes with `@Validated` (e.g., Keycloak URIs, Backend URLs, Limits).
- **Configuration Validation:** Add constraints (`@NotBlank`, `@URL`, `@Min/@Max`) and test misconfigurations during startup (`test` profile).
- **WebClient Centralization:** Centralize `WebClient` configuration (Base URL, Default Headers, Timeouts for Connect/Read/Write).
- **Resilience:** Integrate Resilience4j (Timeout, Retry, CircuitBreaker, Bulkhead) for backend calls from the `frontend` module.
- **Error Path Testing:** Write tests with `MockWebServer`/`WireMock` for error paths.

### API Versioning & Deprecation
- **Semantic Versioning:** All REST API endpoints must be versioned semantically (e.g., `/api/v1/...`). Breaking changes require a new version (e.g., `/api/v2/...`).
- **Deprecation Policy:** When an endpoint is deprecated, annotate it with `@ApiDeprecation(sunset = "YYYY-MM-DD", replacement = "/api/v2/...")` (or use `@Deprecated`).
  - This custom annotation automatically sets the `Deprecation`, `Sunset`, and `Link` HTTP headers via `DeprecationInterceptor`.
  - The `OpenApiDeprecationConfig` customizer ensures the deprecation, sunset date, and migration path are documented in the OpenAPI specification.
