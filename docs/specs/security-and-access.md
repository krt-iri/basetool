> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
> **Owner area:** AUTH/SEC · **Related ADRs:** [ADR-0001](../adr/0001-frontend-confidential-oauth2-client.md) · **Role matrix:** [`ROLES_AND_PERMISSIONS.md`](../../ROLES_AND_PERMISSIONS.md)

# Security & access control

## Context & goal

Both modules run Spring Security on Keycloak OIDC. Authorization is centralised and
enforced architecturally so business logic never carries ad-hoc checks, and every
read/write is isolated to the calling user unless the caller is privileged.

## Requirements

### REQ-SEC-001 — OIDC topology

The backend is a **resource server** (validates the JWT); the frontend is an **OAuth2
client** (browser SSO + bearer-token relay to the backend).

### REQ-SEC-002 — Centralised authorization

Authorization lives in `@PreAuthorize` annotations on services/controllers — never in
business logic. Roles mapped from the JWT are prefixed `ROLE_` and uppercased.

### REQ-SEC-003 — Architectural invariants (ArchUnit-enforced)

The following must always hold and are enforced as ArchUnit rules in
[`ArchitectureTest`](../../backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java)
(backend) and the frontend equivalent — a new violation fails `./gradlew test`:

- No `SecurityContextHolder` use outside the auth-helper service.
- Every `@RestController` carries at least one `@PreAuthorize`.
- Controllers never return JPA entities (DTOs only — see [`api-conventions.md`](api-conventions.md)).
- The frontend does not depend on Spring Data JPA.

### REQ-SEC-004 — Roles & hierarchy

Roles: `ADMIN`, `OFFICER`, `LOGISTICIAN`, `MISSION_MANAGER`, `SQUADRON_MEMBER`, `GUEST`.
Hierarchy: `ADMIN > LOGISTICIAN`, `ADMIN > MISSION_MANAGER`, `OFFICER > LOGISTICIAN`,
`OFFICER > MISSION_MANAGER`. The full matrix is authoritative in
[`ROLES_AND_PERMISSIONS.md`](../../ROLES_AND_PERMISSIONS.md).

### REQ-SEC-005 — Contextual LOGISTICIAN / MISSION_MANAGER grants

`LOGISTICIAN` and `MISSION_MANAGER` are granted contextually via `is_logistician` /
`is_mission_manager` flags on `org_unit_membership` rows; the flag on *any* membership
yields the flat authority via `CustomJwtGrantedAuthoritiesConverter`, with per-OrgUnit
scoping enforced by `@PreAuthorize` SpEL against `OwnerScopeService.canEditOrgUnit(...)`.
An SK `is_lead` membership additionally grants both roles (flat + contextual
`LOGISTICIAN@skId` / `MISSION_MANAGER@skId`) on that SK. Legacy `app_user.is_logistician`
/ `is_mission_manager` are read only as a fallback for users with no membership row;
dropped in the destructive cleanup release.

### REQ-SEC-006 — Multi-user data isolation

Every read/write filters by JWT `sub` unless the caller has an elevated role (`ADMIN`,
`OFFICER`, …). **Enforce this in the service layer, not the controller.**

### REQ-SEC-007 — Guest minimisation & field redaction

For unauthenticated guests, return only the minimum required data. Sensitive fields
(email, real name, internal orders/items) MUST be explicitly cleared in the controller via
a `cleanupForGuest`-style helper to prevent information disclosure. (E-mail is shown only in
a user's own profile — never elsewhere.)

### REQ-SEC-008 — Frontend bot protection & silent re-auth

The frontend's `BotProtectionFilter` returns 404 directly for known scanner paths;
`SsoReAuthenticationEntryPoint` gives legitimate paths with expired sessions a silent
`prompt=none` Keycloak redirect.

## Out of scope

OrgUnit scoping/visibility rules (see [`org-unit-tenancy.md`](org-unit-tenancy.md)); the
confidential-client migration decision (see ADR-0001).
