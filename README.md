# IRIDIUM Basetool

This project is a squadron management tool for **IRIDIUM**. It provides a central platform for mission planning, hangar management, and user administration.

## Personal Inventory

The application provides a personal-inventory feature that lets every authenticated member
keep track of their own items (with name, optional note, quantity and a UEX City or Space
Station as location).

- User area: `/personal-inventory` — list, create, edit and delete only own entries.
  Confirmation dialogues use KRT-styled modals (no native `confirm()`/`alert()`).
- Admin area: `/admin/personal-inventory` (role `ADMIN`) — pick a member from the dropdown
  and manage that member's inventory (clearly marked as `ADMIN MODE`).
- Backend endpoints: `/api/v1/personal-inventory` (user), `/api/v1/admin/personal-inventory`
  (admin) and `/api/v1/uex/locations/search` (typeahead). All endpoints are paginated,
  validated (`jakarta.validation`) and protected by optimistic locking via the `version`
  field on the update DTO.
- Location data is sourced from the locally synchronized UEX `City` and `SpaceStation`
  tables (`UexUniverseSyncService`); no live UEX call is performed on the hot path.
- Translations live in `personalInventory.*` and `admin.personalInventory.*` and are
  available in German and English.

## Project Structure

The project is divided into two main modules:
*   **`backend`**: A Spring Boot application providing the REST API and managing data.
*   **`frontend`**: A Spring Boot application with Thymeleaf for the user interface.

Plus a few smaller top-level directories:
*   **`keycloak-theme/krt-theme`**: Custom Keycloak login/account UI theme that
    matches the IRIDIUM Basetool corporate design — orange `#E77E23` accents,
    `Ethnocentric` and `Lato` web fonts, German default locale (`de`, `en`). See
    [Keycloak Theme](#keycloak-theme) below for details.
*   **`design`**: Source assets for the brand (logos, mock-ups, the
    `Styleguide.md` reference). Not consumed by the runtime, kept in the
    repo so designers and developers share one source of truth.
*   **`scripts`**: One-off Python helper scripts used for repository maintenance
    (i18n key sync, umlaut escaping, untranslated-string detection, etc.).

## Keycloak Theme

The custom theme lives under `keycloak-theme/krt-theme/` and contains two
FreeMarker theme families:

| Folder | Used for | Parent | Locales |
|---|---|---|---|
| `login/` | The Keycloak login flow (`login.ftl`, OTP, password reset, ...) | `keycloak` (Keycloak's classic theme) | `de` (default), `en` |
| `account/` | The user-facing self-service Account Console | `keycloak.v3` (Keycloak's modern v3 account theme) | `de` (default), `en` |

Both flavours pull in `Ethnocentric` and `Lato` font faces from
`<flavour>/resources/fonts/` and a single CSS file
(`login/resources/css/krt-login-v3.css` and
`account/resources/css/krt-account-v3.css`) that overrides the parent theme's
colours and typography to match the corporate design described in
[Styleguide.md](Styleguide.md).

### Wiring

`docker-compose.yml` bind-mounts the theme directory directly into the Keycloak
container so any edit takes effect on the next container restart, with no rebuild:

```yaml
keycloak:
  volumes:
    - ./keycloak-theme/krt-theme:/opt/keycloak/themes/krt-theme
```

The realm export (`realm-export.json`, also bind-mounted into
`/opt/keycloak/data/import/`) sets the per-realm `loginTheme` and `accountTheme`
to `krt-theme`, so the customisation activates as soon as Keycloak boots.

### Making theme changes

1. Edit the relevant FreeMarker template (`login/login.ftl`, ...) or CSS file
   under `keycloak-theme/krt-theme/<login|account>/`.
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

## Tech Stack

*   **Language**: Java 25
*   **Framework**: Spring Boot 4.0.4
*   **Database**: PostgreSQL 18
*   **Security**: Spring Security with OAuth2 (Keycloak)
*   **Frontend**: Thymeleaf, Spring Security OAuth2 Client
*   **Build Tool**: Gradle 9.4.1 (Kotlin DSL)
*   **Containerization**: Docker & Docker Compose

## Prerequisites

*   [Java 25](https://adoptium.net/) (for local development)
*   [Docker](https://www.docker.com/) & Docker Compose
*   Access to a Keycloak server (provided via Docker Compose)

## Configuration

Both modules use environment variables for key configurations.

| Variable | Description | Default |
| :--- | :--- | :--- |
| `KEYCLOAK_ISSUER_URI` | The URL of the Keycloak realm. | `https://keycloak.iri-base.org/realms/iri` |
| `KEYCLOAK_CLIENT_SECRET`| (Frontend only) The secret for the Keycloak client. | `YOUR_CLIENT_SECRET` |
| `BACKEND_URL` | (Frontend only) The URL of the backend API. | `http://localhost:11261` |
| `APP_LOGGING_CORRELATION_ID_HEADER` | HTTP header used for inbound/outbound request correlation (MDC-backed). | `X-Correlation-Id` |
| `APP_LOGGING_SLOW_REQUEST_THRESHOLD_MS` | Threshold (ms) above which a request is logged at `WARN` instead of `INFO`. | `2000` |
| `APP_LOGGING_STRUCTURED_ENABLED` | Enables the JSON (Logstash) log appender. Automatically `true` in the `prod` profile. | `false` (dev/test), `true` (prod) |
| `APP_LOGGING_SLOW_BACKEND_CALL_THRESHOLD_MS` | (Frontend only) Threshold (ms) above which an outbound backend call is logged at `WARN`. | `1500` |

### Session Persistence (Redis)

Spring Sessions werden persistent in Redis gespeichert (`spring-session-data-redis`). Nach einem Neustart des Frontend-Containers bleiben alle aktiven Nutzersessions erhalten — kein sichtbarer Re-Login-Flow, keine 302-Redirects. Redis läuft als eigener Docker-Service (`redis` / `redis-dev`) im Netzwerk `net-redis-frontend` und ist für den Frontend-Container unter dem Hostnamen `redis` erreichbar. Das Passwort wird über die Umgebungsvariable `REDIS_PASSWORD` konfiguriert (siehe `.env`).

Der `SsoReAuthenticationEntryPoint` bleibt als Fallback für wirklich abgelaufene Sessions (Session-Timeout) aktiv. Er erkennt bekannte Bot-/Scanner-Pfade (z. B. `/wp-admin/`, `/robots.txt`, `/feed/`) und beantwortet diese direkt mit HTTP 404, ohne einen OAuth2-Flow auszulösen. Für legitime App-Pfade mit abgelaufener Session wird ein stiller Keycloak-SSO-Redirect (`prompt=none`) ausgeführt.

### Request Correlation & Structured Logging

Both the backend and the frontend module emit one access-log line per request (HTTP method, path, status, duration) and enrich **every** log line with two MDC fields:

- `correlationId` — taken from the inbound `X-Correlation-Id` request header (configurable via `APP_LOGGING_CORRELATION_ID_HEADER`) or generated as UUID if absent. The effective id is echoed back in the response header of the same name so clients, proxies and load balancers can trace requests end-to-end. The frontend's `WebClientLoggingFilter` also propagates the same id to every outbound backend call, so backend and frontend log lines of the same user interaction share one id.
- `userId` — the JWT / OIDC `sub` claim of the authenticated user, or `anonymous` for unauthenticated traffic. Names, emails and tokens are never logged.

In addition to access logs, the frontend module logs Resilience4j events (circuit-breaker state transitions, retry attempts, bulkhead rejections, time-limiter timeouts) via a dedicated `ResilienceEventLogger` so that degraded-backend symptoms such as `SERVICE_UNAVAILABLE` / `BACKEND_TIMEOUT` always carry a matching log entry explaining why.

In the `prod` profile both applications additionally write a structured JSON log (`logs/backend.json` / `logs/frontend.json`) via `LogstashEncoder`, making the logs ready for ELK / Loki / CloudWatch ingestion. Error events are rolled into dedicated `*-error.log` files for fast incident triage.

## Getting Started

Prior to running any Docker Compose commands, ensure you have created a `.env` file in the root directory. Copy `.env.example` and fill in real values - the compose stack uses `${VAR:?...}` syntax and refuses to start if any required variable is missing.

> [!IMPORTANT]
> **Do not ship the placeholder values shown below into production.** They are
> illustrative only. Generate strong passwords (e.g. `openssl rand -base64 32`)
> for every credential and rotate them before the first deployment. The
> Keycloak bootstrap admin in particular is the realm-master account; an
> `admin` / `admin` setup makes the entire identity provider trivially
> compromisable.

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

REDIS_PASSWORD=CHANGE_ME
```

### Running with Docker Compose (Production)

This is the easiest way to run the entire isolated stack (Databases, Backend, Frontend, Keycloak, NGINX Proxy Manager) using dedicated docker networks. Data is persisted in `/var/iri`.

1.  **Start Services**:
    ```bash
    docker compose --profile prod up -d
    ```

2.  **Access**:
    *   **NGINX Proxy Manager UI**: `http://localhost:10081` (Port 10080 for HTTP, 10443 for HTTPS)
    *   **Frontend (via NPM)**: Configure proxy in NPM to forward to `frontend:18081`
    *   **Backend API (Internal)**: `backend:11261`
    *   **Keycloak (Internal)**: `keycloak:18080`

### Running with Docker Compose (Full Dev Stack)

To run the whole stack with exposed host ports for development:

1.  **Start Services**:
    ```bash
    docker compose --profile dev up -d
    ```

2.  **Access**:
    *   **Frontend**: `http://localhost:18081`
    *   **Backend API**: `http://localhost:11261`
    *   **Swagger UI**: `http://localhost:11261/swagger-ui.html`
    *   **Keycloak**: `http://localhost:18080`

### Local Development Setup

To run the Java applications locally (outside Docker) for development:

1.  **Start Dependencies**:
    Start the PostgreSQL databases and Keycloak using the `dev` profile. This exposes ports `15432`, `15433` and `18080` on your host.
    ```bash
    docker compose --profile dev up -d db-backend-dev db-keycloak-dev keycloak-dev
    ```

2.  **Run Backend**:
    Open a terminal and run the backend module (uses `dev` profile by default).
    ```bash
    ./gradlew :backend:bootRun
    ```
    The backend will be available at `http://localhost:11261`.

3.  **Run Frontend**:
    Open another terminal and run the frontend module (uses `dev` profile by default).
    ```bash
    ./gradlew :frontend:bootRun
    ```
    The frontend will be available at `http://localhost:18081`.

*Note: Ensure `KEYCLOAK_ISSUER_URI` is accessible from your machine. If you need to override it, set the environment variable before running the commands.*

#### Database Details

*   **Backend DB (krt_basetool)**:
    *   **Username**: `krt_user` (from `.env`)
    *   **Password**: `krt_password` (from `.env`)
    *   **Port**: `15432`
*   **Keycloak DB (keycloak)**:
    *   **Username**: `krt_keycloak_user` (from `.env`)
    *   **Password**: `krt_keycloak_password` (from `.env`)
    *   **Port**: `15433`


## Error Format (RFC 7807 Problem Details)

All API errors are returned using RFC 7807 Problem Details with content type `application/problem+json`.

- Fields: `type` (URI), `title` (short summary), `status` (HTTP status), `detail` (human-readable explanation), `instance` (request URI)
- Validation errors additionally include an `errors` object with field->message pairs.

Example:
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

Swagger UI documents standard 4xx/5xx responses with this schema.

## API Versioning and Deprecation

The REST API uses semantic versioning via URI paths (e.g., `/api/v1/...`). 
Endpoints slated for removal will be marked as deprecated in the OpenAPI specification and will return the following HTTP headers:
- `Deprecation: true`
- `Sunset: <Date>` (e.g., `Thu, 31 Dec 2026 00:00:00 GMT`)
- `Link: <replacement-url>; rel="alternate"` (providing the migration path)
