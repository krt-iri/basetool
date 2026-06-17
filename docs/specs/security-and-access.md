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

The flat authority is the OR-union over *all* of a caller's memberships and therefore carries
**no** OrgUnit context on its own — it MUST never authorise a write against another OrgUnit's
aggregate by itself. Every elevated-mission-role write path (mission edit, manager/owner change,
**and participant management** — check-in/out, attribute edit, payout-preference, removal) gates the
flat role behind `OwnerScopeService.canEditMission(...)`; `MissionSecurityService.canAccessParticipant`
does so by delegating its management branch to `canManageMission` rather than short-circuiting on the
bare `ROLE_MISSION_MANAGER` authority. (Unlinked **guest** participants stay openly editable per
REQ-SEC-009; this scope gate applies only to *user-linked* participants.)

### REQ-SEC-006 — Multi-user data isolation

Every read/write filters by JWT `sub` unless the caller has an elevated role (`ADMIN`,
`OFFICER`, …). **Enforce this in the service layer, not the controller.**

### REQ-SEC-007 — Guest minimisation & field redaction

For unauthenticated guests, return only the minimum required data. Sensitive fields
(email, real name, internal orders/items) MUST be explicitly cleared in the controller via
a `cleanup…ForGuest`-style helper to prevent information disclosure. (E-mail is shown only in
a user's own profile — never elsewhere.) Mission reads have **two** redaction tiers: a
member-peer tier (`cleanupMissionForGuest`, strips owner/managers/PII but keeps the roster
for a fellow member) and the **outsider** tier (`cleanupOutsiderMissionForGuest` = the
member-peer redaction plus the free-text description hidden) used for anonymous and GUEST
callers — see REQ-SEC-009. The naming convention
(`cleanup…ForGuest`) is enforced structurally by the ArchUnit rule
`anonymousReadableMissionEndpointsMustRedactGuestPii`.

### REQ-SEC-009 — Anonymous & guest-role access surface

The application has a deliberately public surface so requesters and visitors can interact
without a login. That surface is **minimal and identical for two cohorts** — *anonymous*
callers (no JWT) and the *GUEST role* (an authenticated Keycloak user with no member or
elevated authority). The discriminator is `AuthHelperService.isMemberOrAbove()` (true for
`ADMIN`/`OFFICER`/`MISSION_MANAGER`/`LOGISTICIAN`/`SQUADRON_MEMBER`/`MEMBER`); its negation
is the **"mission outsider"** predicate. A GUEST is treated exactly like an anonymous
visitor on the mission surface — *behandle guest wie anonym bei den Einsätzen*.

What a mission outsider (anonymous OR GUEST) **may** do — and nothing more:

- **Orders:** create a job order only (`POST /api/v1/orders`, `/api/v1/orders/items`, plus
  the supporting `permitAll` catalog reads). They may **not** list, view, edit or delete
  orders. (This holds for GUEST too: a memberless account fails the profit-eligibility gate
  `canViewJobOrders`, exactly like an anonymous caller — see `org-unit-tenancy.md`.)
- **Missions (non-internal only):** see the mission detail in its **redacted** form, sign up
  as a participant, and edit / check-in / check-out / delete / change-payout-preference on
  **unlinked guest participants** (`participant.user == null`, which includes their own
  guest entry) via `MissionSecurityService.canAccessParticipant`. Internal and past
  (`COMPLETED`/`CANCELLED`) missions are not visible to outsiders.

The outsider mission detail (`MissionController.cleanupOutsiderMissionForGuest`) applies the
member-peer redaction (participant PII stripped to the public callsign tuple
username/displayName/rank; owner, managers and internal inventory/refinery cleared) and
**additionally hides only the free-text `description`**. By explicit product decision an
outsider **does** see, on a non-internal mission, the owning **organisation**
(`owningSquadron`), the **participant roster** (PII-stripped) with each participant's
**payout preference**, the assigned **units** and the mission **frequencies**. PII (email,
real name) is never included — that is a non-negotiable invariant regardless of which fields
are shown.

The mission **finance ledger** (`GET`/`POST /api/v1/.../finance-entries`) is a separate
surface — the per-participant payout *preference* above is not the ledger — and stays
restricted to **registered members and above**: anonymous AND GUEST are blocked (create +
read). Finance-entry creation is therefore no longer anonymous.

**Acceptance**

- [ ] Anonymous and GUEST callers can `POST /api/v1/orders` (+`/items`) but receive empty
  list / 403 on every order read/edit/delete path.
- [ ] A mission outsider's `GET /api/v1/missions/{id}` on a non-internal mission returns a DTO
  with `description`, `owner`, `managers` null and internal inventory/refinery empty, but WITH
  the participant roster (PII stripped — no email/roles), `owningSquadron`, `assignedUnits` and
  `frequencies` present; internal/past → 403.
- [ ] An outsider can add and edit an unlinked guest participant; editing a *linked*
  participant they do not own → 403.
- [ ] Anonymous create on `POST /api/v1/finance-entries` → 401; GUEST → 403; member → 201.
  GUEST `GET /api/v1/missions/{id}/finance-entries` → 403.
- [ ] `AuthHelperService.isMemberOrAbove()` is false for anonymous and GUEST, true for every
  member/elevated role.

**Enforced by:** `MissionControllerLifecycleTest`, `MissionDataLeakTest`,
`MissionGuestAccessTest`, `MissionFinanceEntryControllerSecurityTest`, `AuthHelperServiceTest`,
`ArchitectureTest#anonymousReadableMissionEndpointsMustRedactGuestPii` · **Code:**
`MissionController`, `MissionFinanceEntryController`, `AuthHelperService`,
`SecurityConfig` · **Role matrix:** [`ROLES_AND_PERMISSIONS.md` §1](../../ROLES_AND_PERMISSIONS.md)

### REQ-SEC-008 — Frontend bot protection & silent re-auth

The frontend's `BotProtectionFilter` returns 404 directly for known scanner paths;
`SsoReAuthenticationEntryPoint` gives legitimate paths with expired sessions a silent
`prompt=none` Keycloak redirect.

### REQ-SEC-010 — AJAX CSRF token refresh endpoint

The frontend's session/meta CSRF setup is unchanged (`HttpSessionCsrfTokenRepository` +
`XorCsrfTokenRequestAttributeHandler`). An additive authenticated endpoint `GET /csrf` returns
`{headerName, token}` so the shared `krtCsrf` client (REQ-FE-004,
[`frontend-ajax-mutations.md`](frontend-ajax-mutations.md)) can self-heal a bare-403 write with a
single transparent token refresh + retry. The endpoint sits under the `authenticated()` catch-all —
an anonymous caller is redirected to the OIDC entry point, never handed a token — so it widens no
trust boundary and is not a change to the CSRF repository/handler (ADR-0012).

**Acceptance**

- [ ] `GET /csrf` returns the active header name + token for an authenticated session.
- [ ] `GET /csrf` does not serve a token to an anonymous caller.

**Enforced by:** `CsrfTokenControllerMvcTest` · **Code:** `CsrfTokenController` · **Issues:** #572

### REQ-SEC-011 — Rate-limit client-IP attribution

The backend's per-IP / per-endpoint rate limiter (`RateLimitingFilter`) is only meaningful if it can
tell clients apart. Because the backend is a pure resource server reached **only** server-side by the
frontend (no browser hits it directly), the frontend MUST relay the originating client IP on every
outbound backend call as `X-Forwarded-For` (`ClientIpRelayFilter`, snapshotted per request by
`ClientIpContextFilter` and carried across the Reactor hop via the registered `ThreadLocalAccessor`).
The backend honours `X-Forwarded-For` only from its configured `app.rate-limit.trusted-proxies` (the
frontend container) and reads the first hop as the client. Without the relay every per-IP bucket
collapses onto the single frontend container IP, so one caller can trip a public endpoint's budget for
the whole organisation. The relay never overwrites an existing header and degrades silently to
"frontend IP" for background tasks with no bound request.

**Acceptance**

- [ ] A backend call issued for a browser request carries `X-Forwarded-For` with the real client IP.
- [ ] Two distinct clients hitting the same anonymous endpoint consume separate per-IP buckets.

**Enforced by:** `ClientIpRelayFilterTest` · **Code:** `ClientIpRelayFilter` / `ClientIpContextFilter`
/ `RateLimitingFilter.resolveClientIp` · **Issues:** security audit DOS-1

## Out of scope

OrgUnit scoping/visibility rules (see [`org-unit-tenancy.md`](org-unit-tenancy.md)); the
confidential-client migration decision (see ADR-0001).
