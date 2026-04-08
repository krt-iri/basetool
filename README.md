# IRIDIUM Basetool

This project is a squadron management tool for **IRIDIUM**. It provides a central platform for mission planning, hangar management, and user administration.

## Project Structure

The project is divided into two main modules:
*   **`backend`**: A Spring Boot application providing the REST API and managing data.
*   **`frontend`**: A Spring Boot application with Thymeleaf for the user interface.

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

## Getting Started

Prior to running any Docker Compose commands, ensure you have created a `.env` file in the root directory containing the required database credentials:

```env
POSTGRES_DB=krt_basetool
POSTGRES_USER=krt_user
POSTGRES_PASSWORD=krt_password
KC_POSTGRES_DB=keycloak
KC_POSTGRES_USER=krt_keycloak_user
KC_POSTGRES_PASSWORD=krt_keycloak_password
KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=admin
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
