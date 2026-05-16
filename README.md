# IRIDIUM Basetool

The IRIDIUM Basetool is the squadron-management web app for the
"DAS KARTELL" / IRIDIUM organization in *Star Citizen*. It provides a central
platform for mission planning, hangar and inventory tracking, refinery and
material logistics, terminal data, and member administration — backed by
single-sign-on via Keycloak and a clear role and permission model.

---

## 1. Overview

### What the application provides

- **Mission planning** — plan, brief and review squadron missions with role-aware access (`MISSION_MANAGER`, `OFFICER`, `ADMIN`).
- **Hangar & inventory** — track ships and personal inventories per member, including UEX City and Space Station locations.
- **Refinery & materials** — manage refinery job orders, material handovers and the materials matrix (`/materials/overview`) with planet-aware grouping.
- **Terminals** — administer trade terminals, including UEX raw state (loading dock, auto-load) and the last UEX sync timestamp (`/admin/terminals`).
- **User administration** — manage members, roles and the `LOGISTICIAN` / `MISSION_MANAGER` capability flags.
- **Personal inventory** — every authenticated member maintains their own item list at `/personal-inventory`; admins manage other members' inventories at `/admin/personal-inventory`. Backend endpoints under `/api/v1/personal-inventory` (user) and `/api/v1/admin/personal-inventory` (admin) are paginated, validated, and protected by optimistic locking.
- **i18n** — every user-visible string is fully translated (German default, English).
- **Custom Keycloak theme** — login and account console in the IRIDIUM corporate design.

### High-level architecture

```
┌──────────────┐         ┌─────────────┐         ┌──────────────┐
│   Browser    │ ──SSO──►│   Keycloak  │◄────────│   Backend    │
│              │         │  (OIDC IdP) │  JWT    │ (REST, JPA)  │
└──────┬───────┘         └─────────────┘         └──────┬───────┘
       │                                                 │
       │                ┌─────────────┐                  │
       └───HTML/CSS────►│  Frontend   │──WebClient──────►│
                        │ (Thymeleaf) │   bearer-token   │
                        └──────┬──────┘                  │
                               │                         │
                               ▼                         ▼
                         ┌─────────┐               ┌──────────┐
                         │  Redis  │               │ Postgres │
                         │(session)│               │  (data)  │
                         └─────────┘               └──────────┘
```

- **Backend** — REST API only (`/api/v1/...`), Spring Boot 4 on Java 25, JPA / Flyway / PostgreSQL.
- **Frontend** — Thymeleaf-rendered UI calling the backend via a centrally-configured WebClient (Resilience4j wrapped). No direct database or Keycloak Admin API access.
- **Keycloak** — OAuth2 / OIDC identity provider, custom IRIDIUM theme.
- **Redis** — Spring Session store; sessions survive frontend restarts.

---

## 2. Project Documentation

The README focuses on getting the project up and running. The following
documents cover everything else:

| Document | Purpose |
| :--- | :--- |
| [CHANGELOG.md](CHANGELOG.md) | Release notes and every user-visible change. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to report bugs, suggest features and submit pull requests, plus the coding style guide. |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community standards (Contributor Covenant 3.0). |
| [.github/SECURITY.md](.github/SECURITY.md) | Security policy — how to report a vulnerability via GitHub Private Vulnerability Reporting, supported versions, scope, safe harbor, release verification (Cosign, SLSA, SBOM). |
| [LICENSE.md](LICENSE.md) | GNU General Public License v3.0. |
| [ROLES_AND_PERMISSIONS.md](ROLES_AND_PERMISSIONS.md) | Full role and permission matrix (`ADMIN`, `OFFICER`, `LOGISTICIAN`, `MISSION_MANAGER`, `SQUADRON_MEMBER`, `GUEST`). |
| [Styleguide.md](Styleguide.md) | "DAS KARTELL" Corporate Design Manual — brand colors, typography (`Ethnocentric`, `Lato`), department palette, visual rules. |
| [docs/deployment.md](docs/deployment.md) | Production deployment runbook — host bootstrap, normal releases, manual rollback, PAT rotation, troubleshooting. |
| [backend/src/main/resources/db/migration/README.md](backend/src/main/resources/db/migration/README.md) | Flyway migration conventions — destructive-ops two-phase rule, data-migration patterns, performance / locking, pre-merge checklist. |
| [CLAUDE.md](CLAUDE.md) | Project-specific guidance for the Claude Code AI assistant — build / run / test commands, architectural invariants, conventions. |
| [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) | The pull-request template that ships with every PR. |
| [impressum.md](impressum.md) | Imprint (legally required for the deployed application). |

---

## 3. Deployment

> [!IMPORTANT]
> **Never ship the placeholder credentials shown below into production.**
> Generate strong values (`openssl rand -base64 32`) for every secret and
> rotate them before the first deployment. The Keycloak bootstrap admin in
> particular is the realm-master account; an `admin` / `admin` setup makes
> the entire identity provider trivially compromisable.

### 3.1 Environment file

Both deployment paths read a `.env` file at the repository root. Copy
`.env.example` and replace every `CHANGE_ME`:

```env
# Backend Database configuration
POSTGRES_DB=krt_basetool
POSTGRES_USER=CHANGE_ME
POSTGRES_PASSWORD=CHANGE_ME

# Keycloak Database configuration
KC_POSTGRES_DB=keycloak
KC_POSTGRES_USER=CHANGE_ME
KC_POSTGRES_PASSWORD=CHANGE_ME

# Keycloak Initial Admin User
KC_BOOTSTRAP_ADMIN_USERNAME=CHANGE_ME
KC_BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME

# Keycloak admin-API client secret (used by backend to sync users).
# Get / rotate this in the Keycloak admin console:
#   Realm "iri" -> Clients -> backend-service -> Credentials -> Regenerate.
KEYCLOAK_ADMIN_CLIENT_SECRET=CHANGE_ME

# PKCS12 keystore password for backend + frontend Spring SSL.
SERVER_SSL_KEY_STORE_PASSWORD=CHANGE_ME

# Absolute host path of the production keystore.p12. Bind-mounted read-only
# into backend + frontend at /run/secrets/keystore.p12. The keystore is
# NEVER baked into the GHCR image (see CLAUDE.md and .dockerignore).
IRI_KEYSTORE_HOST_PATH=/var/iri/secrets/keystore.p12

REDIS_PASSWORD=CHANGE_ME
```

Compose uses `${VAR:?...}` references throughout — if any required variable
is missing, the stack refuses to start.

### 3.2 Production deployment (GHCR pull + systemd timer)

Production hosts do **not** build images locally. The
[release-images](.github/workflows/release-images.yml) GitHub Actions
workflow builds, scans (Trivy), signs (Cosign keyless / Sigstore) and
pushes the backend + frontend images to GHCR on every push to `main` and
every `v*.*.*` tag. The
[promote](.github/workflows/promote.yml) workflow re-tags an existing
digest as `:stable` when an operator decides it should go live. The
production host polls `:stable` every five minutes via `iri-deploy.timer`
and applies any new digest with health-check-gated rollback.

The end-to-end runbook lives in [**docs/deployment.md**](docs/deployment.md).
Summary of the release loop:

1. **Build + push**: `git tag -a v1.4.3 -m "..." && git push origin v1.4.3`
   → fires `release-images.yml` → images appear in GHCR as `:1.4.3`,
   `:1.4`, `:1`, `:latest`. Nothing is deployed yet.
2. **Promote**: `gh workflow run promote.yml -f version=1.4.3` → flips
   `:stable` to the same digest. Still nothing is deployed; the server
   polls the change within five minutes.
3. **Pull + apply**: `iri-deploy.timer` fires → `scripts/deploy.sh`
   resolves the new `:stable` digest, pins it in a compose override,
   runs `docker compose pull && docker compose up -d --wait` with a
   180 s health-check, and auto-rolls-back to the previous digest if
   the new images fail to become healthy.

### 3.3 Running locally with Docker Compose (pulling from GHCR)

Same images, same orchestration as production, but on your own machine:

1. **Authenticate to GHCR** (one-time): create a fine-grained PAT with
   `Packages: Read` on `krt-iri/basetool`, then
   ```bash
   echo "$GHCR_TOKEN" | docker login ghcr.io --username your-gh-handle --password-stdin
   ```

2. **Provide a `keystore.p12`** at the path your `.env`'s
   `IRI_KEYSTORE_HOST_PATH` points to (default: repo root `./keystore.p12`).
   See [§4.4 Running the Local Test Stack](#44-running-the-local-test-stack)
   for the `keytool` recipe.

3. **Start**:
   ```bash
   docker compose --profile prod up -d         # pulls :stable
   # or pin a specific version:
   IRI_BASETOOL_VERSION=1.4.3 docker compose --profile prod up -d
   ```

Default host ports: backend `11261`, frontend `18081`, Keycloak `18080`,
backend DB `15432`, Keycloak DB `15433`, Redis `6379`, NPM admin `10081`.

---

## 4. Development & Testing

### 4.1 Prerequisites

* [Java 25](https://adoptium.net/) — required for local Gradle builds.
* [Docker](https://www.docker.com/) and Docker Compose — for the dependency
  stack and the full dev / test stacks.
* Access to a Keycloak server — the Docker Compose stack ships one.

The project uses **Gradle 9 with the Kotlin DSL**. Always use the wrapper
(`./gradlew`); never the IDE test runner. Dependencies are managed by
[refreshVersions](https://jmfayard.github.io/refreshVersions/) — edit
`versions.properties`, not `build.gradle.kts`. Run
`./gradlew refreshVersions` to discover updates.

### 4.2 Local development setup (running the apps from Gradle)

Recommended for active development — the apps run on the host JVM with
fast restarts; only the dependencies live in containers.

1. **Start dependencies** (Postgres × 2, Keycloak, Redis):
   ```bash
   docker compose --profile dev up -d db-backend-dev db-keycloak-dev keycloak-dev redis-dev
   ```
   This exposes ports `15432` (backend DB), `15433` (Keycloak DB), `18080`
   (Keycloak) and `6379` (Redis) on the host.

2. **Run the backend** (uses the `dev` profile by default, HTTPS):
   ```bash
   ./gradlew :backend:bootRun
   ```
   Backend at `https://localhost:11261`, Swagger UI at
   `https://localhost:11261/swagger-ui.html`.

3. **Run the frontend** (uses the `dev` profile by default, HTTP):
   ```bash
   ./gradlew :frontend:bootRun
   ```
   Frontend at `http://localhost:18081`.

*If you need to override `KEYCLOAK_ISSUER_URI`, set it as an environment
variable before running the commands.*

**Database details** (defaults from `.env`):
* Backend DB `krt_basetool` — user `krt_user`, port `15432`.
* Keycloak DB `keycloak` — user `krt_keycloak_user`, port `15433`.

### 4.3 Running the full dev stack with Docker Compose

Two flavours, depending on whether you need a local rebuild of the
application image.

**Pulling from GHCR** (default — fast, matches prod):

```bash
docker compose --profile dev up -d              # pulls :stable, exposes host ports
```

**Building locally from this checkout** (when iterating on the Dockerfile):

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml \
    --profile dev up -d --build
```

The `docker-compose.build.yml` override re-introduces `build:` directives
and tags the result as `:local` so it does not collide with GHCR-pulled
images.

Access in either flavour:
* **Frontend**: `http://localhost:18081`
* **Backend API**: `http://localhost:11261`
* **Swagger UI**: `http://localhost:11261/swagger-ui.html`
* **Keycloak**: `http://localhost:18080`

### 4.4 Running the local test stack

For quick UI verification of an in-progress change in a worktree, or for
any scenario where you want to spin up the full stack **without exposing
the production `.env`, the production `keystore.p12`, or the shared
`realm-export.json`** to a transient workspace. This setup uses an isolated
set of throwaway credentials so a forgotten `docker volume prune`, a stray
screenshot, or a CI artifact upload cannot leak real secrets. The rule is
enforced by [CLAUDE.md](CLAUDE.md) → *Testing*; the repo's `.gitignore`
already excludes `.env.*`, `keystore.p12` and `realm-export.json` so the
test artifacts never land in commits.

The procedure assumes you are working in the repository root (or in a
worktree; the commands are identical).

1. **Create `.env.test`** with throwaway credentials. The strings below are
   placeholders — pick anything that is *not* a value you use anywhere else.
   ```env
   # Backend DB (Postgres)
   POSTGRES_DB=krt_basetool_test
   POSTGRES_USER=basetool_test
   POSTGRES_PASSWORD=basetool-test-pw-do-not-use-in-prod

   # Keycloak DB (Postgres)
   KC_POSTGRES_DB=keycloak_test
   KC_POSTGRES_USER=keycloak_test
   KC_POSTGRES_PASSWORD=keycloak-test-pw-do-not-use-in-prod

   # Keycloak bootstrap admin
   KC_BOOTSTRAP_ADMIN_USERNAME=admin
   KC_BOOTSTRAP_ADMIN_PASSWORD=admin-test-pw-do-not-use-in-prod

   # Keycloak admin-API client secret (must match realm-export.json — see below)
   KEYCLOAK_ADMIN_CLIENT_SECRET=test-client-secret-do-not-use-in-prod

   # Redis
   REDIS_PASSWORD=redis-test-pw-do-not-use-in-prod

   # PKCS12 keystore password (must match the test keystore — see below)
   SERVER_SSL_KEY_STORE_PASSWORD=keystore-test-pw-do-not-use-in-prod

   # Host path of the test keystore (bind-mounted into the containers).
   # Default in docker-compose.yml is `./keystore.p12` at the repo root,
   # which lines up with step 2 below.
   IRI_KEYSTORE_HOST_PATH=./keystore.p12
   ```

2. **Generate a test keystore** with the password from step 1. Place it at
   the repo root so the default `IRI_KEYSTORE_HOST_PATH=./keystore.p12`
   from step 1 resolves. Do *not* copy a `keystore.p12` from any other
   checkout — the password would not match and `keytool` errors are hard
   to debug.
   ```bash
   keytool -genkeypair -alias basetool -storetype PKCS12 \
     -keystore ./keystore.p12 \
     -storepass "keystore-test-pw-do-not-use-in-prod" \
     -keypass  "keystore-test-pw-do-not-use-in-prod" \
     -keyalg RSA -keysize 2048 -validity 365 \
     -dname "CN=localhost, OU=Test, O=KRT Basetool Test, L=Test, ST=Test, C=DE" \
     -ext "san=dns:localhost,ip:127.0.0.1,dns:backend,dns:frontend"
   ```

3. **Provide a test `realm-export.json`** at the repo root containing a
   Keycloak realm named `iri` with a `basetool-frontend` public client, a
   `backend-service` confidential client whose `secret` matches
   `KEYCLOAK_ADMIN_CLIENT_SECRET` from `.env.test`, and at least one test
   user. The minimal recipe: copy the production export, replace the
   `backend-service` secret, clear the SMTP block, drop the password
   policy, and replace the `users` array with a single test admin:
   ```python
   # build-test-realm.py — run once, never commit the output
   import json
   r = json.load(open('realm-export.json', encoding='utf-8'))   # production source (separate checkout)
   r['smtpServer'] = {}
   r.pop('passwordPolicy', None)
   for c in r['clients']:
       if c['clientId'] == 'backend-service':
           c['secret'] = 'test-client-secret-do-not-use-in-prod'
           c.setdefault('attributes', {}).pop('client.secret.creation.time', None)
       if c['clientId'] == 'basetool-frontend':
           c['redirectUris'] = ['http://frontend:18081/*', 'http://localhost:18081/*']
           c['webOrigins']   = ['http://frontend:18081', 'http://localhost:18081']
   r['users'] = [{
       'username': 'test-admin',
       'enabled': True, 'emailVerified': True,
       'email': 'test-admin@example.test',
       'firstName': 'Test', 'lastName': 'Admin',
       'credentials': [{'type': 'password', 'value': 'test-admin-pw', 'temporary': False}],
       'realmRoles': ['Admin', 'Officer', 'Squadron Member', 'default-roles-iri',
                      'offline_access', 'uma_authorization'],
   }]
   json.dump(r, open('realm-export.json', 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
   ```

4. **Use the `docker-compose.test.yml` override.** This file lives in the
   repo root and overrides three things in the base `docker-compose.yml`:
   * the hardcoded production `KEYCLOAK_ISSUER_URI` in the backend / frontend templates,
   * `KC_HOSTNAME=host.docker.internal` on Keycloak so the OIDC issuer URL resolves identically from the host browser (Docker Desktop magic) and from inside containers (`extra_hosts: host-gateway`),
   * the matching `KEYCLOAK_ADMIN_URL`, `KEYCLOAK_REALM` and `KEYCLOAK_ADMIN_CLIENT_ID` on the backend.

5. **One-time cleanup** if a previous Postgres init left stale credentials
   in the bind-mount data dirs (you will see
   `FATAL: password authentication failed` in the logs):
   ```bash
   docker compose --env-file .env.test \
     -f docker-compose.yml -f docker-compose.test.yml --profile dev down
   MSYS_NO_PATHCONV=1 docker run --rm -v /var/iri/db-backend:/data alpine \
     sh -c "rm -rf /data/* /data/.[!.]*"
   MSYS_NO_PATHCONV=1 docker run --rm -v /var/iri/db-keycloak:/data alpine \
     sh -c "rm -rf /data/* /data/.[!.]*"
   ```
   The `MSYS_NO_PATHCONV=1` prefix is only needed on Git Bash for Windows;
   it prevents the shell from translating the `/var/iri/...` Linux path
   into a Windows path inside the Docker CLI.

6. **Start the stack**:
   ```bash
   docker compose --env-file .env.test \
     -f docker-compose.yml -f docker-compose.test.yml --profile dev up -d
   ```

7. **Access** (same ports as the regular dev stack):
   * **Frontend**: `http://localhost:18081`
   * **Backend API**: `https://localhost:11261` (self-signed cert from the test keystore)
   * **Swagger UI**: `https://localhost:11261/swagger-ui.html`
   * **Keycloak**: `http://localhost:18080` — log in with the bootstrap admin from `.env.test`
   * **Realm `iri` test user**: username `test-admin`, password `test-admin-pw`

8. **Tear down** after the verification — leaving a test stack running
   consumes 2+ GB of RAM and the named anonymous volumes will collide
   with the next spin-up:
   ```bash
   docker compose --env-file .env.test \
     -f docker-compose.yml -f docker-compose.test.yml --profile dev down --volumes
   ```

### 4.5 Running tests

Tests force `spring.profiles.active=test`; both `Test` and `BootRun` set
`--enable-native-access=ALL-UNNAMED` and a Mockito agent JVM arg.

```bash
./gradlew test                                              # all tests
./gradlew :backend:test                                     # backend tests only
./gradlew :frontend:test                                    # frontend tests (also produces a JaCoCo report)
./gradlew :backend:test --tests "FullyQualifiedClassName"   # single test class
./gradlew :backend:test --tests "ClassName.methodName"      # single test method
```

ArchUnit rules in [`backend/.../ArchitectureTest.java`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java)
and [`frontend/.../ArchitectureTest.java`](frontend/src/test/java/de/greluc/krt/iri/basetool/frontend/ArchitectureTest.java)
enforce architectural invariants (no `SecurityContextHolder` outside the
auth-helper service, every `@RestController` carries at least one
`@PreAuthorize`, controllers never return JPA entities, the frontend
never depends on Spring Data JPA). Adding a violation fails `./gradlew test`.

### 4.6 Linting, static analysis and SBOM

```bash
./gradlew check                                              # full sweep: Checkstyle + SpotBugs + tests
./gradlew :backend:checkstyleMain :backend:spotbugsMain      # backend lint only
./gradlew :frontend:checkstyleMain :frontend:spotbugsMain    # frontend lint only
./gradlew spotlessApply                                      # auto-format Java sources (run before every push)
./gradlew :backend:cyclonedxBom                              # SBOM into backend/docs/
./gradlew :frontend:cyclonedxBom                             # SBOM into frontend/docs/
```

Reports land under `<module>/build/reports/{checkstyle,spotbugs}/main.{html,xml}`.
Checkstyle runs with `maxWarnings = 0` and Spotless is wired into `check`
via `isEnforceCheck = true` — any unformatted Java file or new Checkstyle
warning fails CI immediately.

---

## 5. Technical Details

### 5.1 Tech stack

* **Language** — Java 25
* **Framework** — Spring Boot 4.0.4
* **Build tool** — Gradle 9 with the Kotlin DSL, dependencies via refreshVersions
* **Database** — PostgreSQL 18, schema owned by Flyway (Hibernate `ddl-auto=validate` everywhere)
* **Session store** — Redis (`spring-session-data-redis`)
* **Security** — Spring Security with OAuth2 / OIDC (Keycloak 26)
* **Frontend** — Thymeleaf, Spring Security OAuth2 Client, WebClient wrapped with Resilience4j (Timeout, Retry, CircuitBreaker, Bulkhead)
* **API documentation** — SpringDoc / OpenAPI, Swagger UI at `/swagger-ui.html`
* **DTO mapping** — MapStruct (`@Mapper(componentModel = "spring")`)
* **Containerization** — Docker and Docker Compose; images published to GHCR, signed with Cosign keyless and shipped with SLSA provenance + SPDX SBOM attestations

### 5.2 Project structure

The project is split into two Spring Boot modules plus a few smaller
top-level directories:

* **`backend`** — REST API only. Layered: `controller` → `service` → `repository` → `model` (JPA entities), with `dto` records, MapStruct `mapper`s, `config` (security, caching, OpenAPI, rate limiting, WebClient), `integration` (UEX external API), `task` (scheduled jobs), `filter`/`interceptor` (correlation ID, deprecation headers), `annotation` (`@ApiDeprecation`).
* **`frontend`** — Thymeleaf server-rendered UI that calls the backend via WebClient. No business logic of its own; `service.BackendApiClient` is the single seam. Persistent state across frontend restarts lives in Redis (Spring Session).
* **`keycloak-theme/krt-theme`** — Custom Keycloak login and account UI theme matching the IRIDIUM corporate design. See [§5.7 Keycloak theme](#57-keycloak-theme).
* **`design`** — Source assets for the brand (logos, mock-ups, the [Styleguide.md](Styleguide.md) reference). Not consumed by the runtime, kept in the repo so designers and developers share one source of truth.
* **`scripts`** — One-off Python helper scripts for repository maintenance (i18n key sync, umlaut escaping, untranslated-string detection, etc.).
* **`docs`** — Long-form documentation, primarily the [deployment runbook](docs/deployment.md).

The frontend never talks to PostgreSQL or the Keycloak Admin API directly.
The backend never serves HTML.

### 5.3 Configuration (environment variables)

Both modules read configuration from environment variables. The most
commonly tuned values:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `KEYCLOAK_ISSUER_URI` | The URL of the Keycloak realm. | `https://keycloak.iri-base.org/realms/iri` |
| `KEYCLOAK_CLIENT_SECRET` | (Frontend only) The secret for the Keycloak client. | `YOUR_CLIENT_SECRET` |
| `BACKEND_URL` | (Frontend only) The URL of the backend API. | `http://localhost:11261` |
| `APP_LOGGING_CORRELATION_ID_HEADER` | HTTP header used for inbound / outbound request correlation (MDC-backed). | `X-Correlation-Id` |
| `APP_LOGGING_SLOW_REQUEST_THRESHOLD_MS` | Threshold (ms) above which a request is logged at `WARN` instead of `INFO`. | `2000` |
| `APP_LOGGING_STRUCTURED_ENABLED` | Enables the JSON (Logstash) log appender. Automatically `true` in the `prod` profile. | `false` (dev / test), `true` (prod) |
| `APP_LOGGING_SLOW_BACKEND_CALL_THRESHOLD_MS` | (Frontend only) Threshold (ms) above which an outbound backend call is logged at `WARN`. | `1500` |
| `IRI_BASETOOL_VERSION` | Image tag pulled by the production compose stack. | `stable` |
| `IRI_IMAGE_NAMESPACE` | GHCR namespace for the image lookup. | `krt-iri` |
| `IRI_KEYSTORE_HOST_PATH` | Absolute host path of the production `keystore.p12`. Bind-mounted read-only into backend + frontend at `/run/secrets/keystore.p12`. | `/var/iri/secrets/keystore.p12` |
| `REDIS_PASSWORD` | Password for the Redis session store. | *(required, no default)* |

Relevant `application-*.yml` settings live in `@ConfigurationProperties`
classes with `@Validated` (Keycloak URIs, backend URLs, limits). Constraints
are enforced via `@NotBlank`, `@URL`, `@Min` / `@Max`; misconfiguration is
caught at startup. See the `*Properties` classes under `config/`.

### 5.4 Session persistence (Redis)

Spring Sessions are persisted in Redis (`spring-session-data-redis`).
After a frontend container restart, all active user sessions remain
intact — no visible re-login flow, no 302 redirects. Redis runs as a
dedicated Docker service (`redis` / `redis-dev`) on the
`net-redis-frontend` network and is reachable from the frontend container
under the hostname `redis`. The password is configured via the
`REDIS_PASSWORD` environment variable (see `.env`).

The `SsoReAuthenticationEntryPoint` remains as a fallback for genuinely
expired sessions (session timeout). It recognises known bot / scanner
paths (`/wp-admin/`, `/robots.txt`, `/feed/`, …) and answers them
directly with HTTP 404, without triggering an OAuth2 flow. For legitimate
app paths with an expired session, it performs a silent Keycloak SSO
redirect (`prompt=none`).

### 5.5 Request correlation and structured logging

Both modules emit one access-log line per request (HTTP method, path,
status, duration) and enrich **every** log line with two MDC fields:

- `correlationId` — taken from the inbound `X-Correlation-Id` request header (configurable via `APP_LOGGING_CORRELATION_ID_HEADER`) or generated as a UUID if absent. The effective id is echoed back in the response header of the same name so clients, proxies and load balancers can trace requests end-to-end. The frontend's `WebClientLoggingFilter` also propagates the same id to every outbound backend call, so backend and frontend log lines of the same user interaction share one id.
- `userId` — the JWT / OIDC `sub` claim of the authenticated user, or `anonymous` for unauthenticated traffic. Names, emails and tokens are never logged.

In addition to access logs, the frontend module logs Resilience4j events
(circuit-breaker state transitions, retry attempts, bulkhead rejections,
time-limiter timeouts) via a dedicated `ResilienceEventLogger` so that
degraded-backend symptoms such as `SERVICE_UNAVAILABLE` / `BACKEND_TIMEOUT`
always carry a matching log entry explaining why.

In the `prod` profile both applications additionally write a structured
JSON log (`logs/backend.json` / `logs/frontend.json`) via `LogstashEncoder`,
making the logs ready for ELK / Loki / CloudWatch ingestion. Error events
are rolled into dedicated `*-error.log` files for fast incident triage.

### 5.6 API conventions

**Error format (RFC 7807 Problem Details).** All API errors are returned
using RFC 7807 Problem Details with content type `application/problem+json`.

* Fields: `type` (URI), `title` (short summary), `status` (HTTP status), `detail` (human-readable explanation), `instance` (request URI).
* Validation errors additionally include an `errors` object with field → message pairs.

```json
{
  "type": "https://iri-base.org/problems/constraint-violation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields have invalid values.",
  "instance": "/api/v1/job-types",
  "errors": {
    "name": "must not be blank"
  }
}
```

Swagger UI documents standard 4xx / 5xx responses with this schema.

**API versioning and deprecation.** The REST API uses semantic versioning
via URI paths (`/api/v1/...`). Endpoints slated for removal are marked as
deprecated in the OpenAPI specification and return the following HTTP
headers:

- `Deprecation: true`
- `Sunset: <Date>` (e.g. `Thu, 31 Dec 2026 00:00:00 GMT`)
- `Link: <replacement-url>; rel="alternate"` (providing the migration path)

**Pagination and sorting.** All list endpoints take Spring's `Pageable`
and return a `PageResponse` wrapper (total elements, pages, current page).
Allowed sort fields are whitelisted in the service layer — user input is
never passed directly to `Sort`.

**Time handling.** All times are stored and processed as `Instant` or
`OffsetDateTime` in UTC; conversion to the user's local timezone happens
in the display layer only.

### 5.7 Keycloak theme

The custom theme lives under `keycloak-theme/krt-theme/` and contains two
FreeMarker theme families:

| Folder | Used for | Parent | Locales |
| :--- | :--- | :--- | :--- |
| `login/` | The Keycloak login flow (`login.ftl`, OTP, password reset, …) | `keycloak` (Keycloak's classic theme) | `de` (default), `en` |
| `account/` | The user-facing self-service Account Console | `keycloak.v3` (Keycloak's modern v3 account theme) | `de` (default), `en` |

Both flavours pull in the `Ethnocentric` and `Lato` font faces from
`<flavour>/resources/fonts/` and a single CSS file
(`login/resources/css/krt-login-v3.css` and
`account/resources/css/krt-account-v3.css`) that overrides the parent
theme's colours and typography to match the corporate design described
in [Styleguide.md](Styleguide.md).

**Wiring.** `docker-compose.yml` bind-mounts the theme directory directly
into the Keycloak container, so any edit takes effect on the next
container restart with no rebuild:

```yaml
keycloak:
  volumes:
    - ./keycloak-theme/krt-theme:/opt/keycloak/themes/krt-theme
```

The realm export (`realm-export.json`, also bind-mounted into
`/opt/keycloak/data/import/`) sets the per-realm `loginTheme` and
`accountTheme` to `krt-theme`, so the customisation activates as soon as
Keycloak boots.

**Making theme changes.**

1. Edit the relevant FreeMarker template (`login/login.ftl`, …) or CSS
   file under `keycloak-theme/krt-theme/<login|account>/`.
2. Restart only the Keycloak container so it re-reads the theme:
   ```bash
   docker compose --profile prod restart keycloak     # or `keycloak-dev` for the dev profile
   ```
   Keycloak caches theme resources by default; the restart bumps the cache.
3. Hard-reload the login / account page (browser cache clear or
   `Ctrl+Shift+R`) — the CSS file is served with a long cache header, so
   without a hard reload you may keep seeing the previous version.

No Java build / Docker rebuild is needed for theme-only edits; only the
volume mount has to be in place and the container has to bounce.

---

## License

IRIDIUM Basetool is released under the [GNU General Public License v3.0](LICENSE.md).
