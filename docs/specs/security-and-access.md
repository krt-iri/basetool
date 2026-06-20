> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-17.
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
[`ArchitectureTest`](../../backend/src/test/java/de/greluc/krt/profit/basetool/backend/ArchitectureTest.java)
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

> **Amended by epic #692 (REQ-SEC-015 / REQ-ORG-015):** Bereich/OL leadership memberships
> (`is_bereichsleiter`/`koordinator`/`operator`, `is_ol_member`) also mint the flat
> `LOGISTICIAN`/`MISSION_MANAGER` authorities **and** contextual `…@orgUnitId` authorities — but one per
> **descendant** Staffel/SK of the leader's scope (Bereich → its children; OL → all), so existing
> per-OrgUnit `@PreAuthorize` SpEL resolves for area leaders without change. This reach is concrete and
> scoped; it grants **no** admin rights.

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

### REQ-SEC-012 — Re-authentication on lost frontend OAuth2 token

When a frontend &rarr; backend call fails with Spring Security's `ClientAuthorizationException`
(`client_authorization_required`, or a refresh-grant `invalid_grant` after a revoked/rotated refresh
token) the user MUST be bounced through a fresh Keycloak login rather than shown an empty page / 500
or a stack-trace log flood. Because the exception is a `RuntimeException` (not an
`AuthenticationException`) it bypasses `SsoReAuthenticationEntryPoint` (REQ-SEC-008), so it is handled
explicitly at the MVC boundary: `BackendApiClient` rethrows it as `ReauthenticationRequiredException`
(logged at DEBUG, no stack trace) and `GlobalExceptionHandler` answers an HTML navigation with a
`302` to `/oauth2/authorization/keycloak` and an AJAX caller with a `401` carrying the
`X-Reauthenticate` header (mirrored in the JSON body) so the shared `krtFetch`/`krtReauth` client
redirects the window; the notification SSE relay pushes a `reauth` event for the same effect. The
exception is added to the Resilience4j `ignoreExceptions` so it is neither retried nor counted toward
the circuit breaker.

To stop the in-session refresh race that produces this (parallel page + SSE + poll requests each
replaying the same refresh token, which Keycloak's rotation + reuse detection then revokes — see
`INGEST_KEYCLOAK_SETUP.md` step 4), the `OAuth2AuthorizedClientManager` is wrapped in a
`SingleFlightAuthorizedClientManager` that serialises refreshes per session and serves a
short-lived freshness cache, issuing at most one refresh-token grant per expiry window. The
single-flight key MUST resolve consistently per session — the session id is recovered from
`RequestContextHolder` when the OAuth2 filter did not attach the servlet request, so the same
session never splits across stripes and the principal fallback is reserved for request-less calls.
The long-lived notification SSE relay (`/notifications/stream`, a 30-minute `SseEmitter`) MUST NOT
drive a refresh. Resolving the bearer **read-only**
(`OAuth2AuthorizedClientRepository.loadAuthorizedClient`) is necessary but **not sufficient** on its
own: attaching an authorized client to a WebClient that still carries the OAuth2 exchange filter
routes the call through `ServletOAuth2AuthorizedClientExchangeFilterFunction.reauthorizeClient`, which
calls `OAuth2AuthorizedClientManager.authorize(...)` *unconditionally* and can therefore refresh (and
write the rotated client back) on a stale/empty single-flight cache. The relay therefore uses a
dedicated `sseWebClient` built **without** the `oauth2Configuration()` filter and sets the read-only
bearer as a plain `Authorization` header, so it is structurally incapable of reaching `authorize` — a
stale online refresh token can neither be replayed nor written back to the session (which would
otherwise trip Keycloak's reuse detection and revoke the SSO session). The snapshot token is relayed
verbatim even when expired; the backend rejects it and the always-on unread-count poll, not the relay,
drives re-authentication. The relay fails soft when no token is bound. The single-flight freshness
margin (`EXPIRY_SKEW`) MUST be ≥ the `RefreshTokenOAuth2AuthorizedClientProvider` clock skew (Spring's
default is 60s), so a freshness-cache hit never serves a token the provider would itself refresh.
Single-flight is JVM-local; horizontally-scaled deployments previously relied on `Refresh Token Max
Reuse > 0` on Keycloak for the residual cross-instance race. `offline_access` MUST NOT be re-added to
paper over this (audit finding L-4). See ADR-0019 (and its 2026-06-18 amendments).

**2026-06-18 — the root mitigation is on Keycloak, not in code.** Three iterations of the code-side
single-flight / relay hardening above did not stop the cascade in production. The failing
refresh-token grant surfaces on the **ordinary** page-render and unread-count-poll path (frontend
stack: `HomeController` → `BackendApiClient` → `ReauthenticationRequiredException` →
`ClientAuthorizationException` → `SingleFlightAuthorizedClientManager.authorize`), not only on the SSE
relay, while the backend stays healthy (every `GET /api/v1/missions/next` that reaches it returns
`200`). Keycloak logged the full reuse-detection chain on one SSO session —
`REFRESH_TOKEN_ERROR reason="Stale token"` → `"Session doesn't have required client"` →
`"refresh token issued before the client session started"`. (The event field
`client_auth_method="client-secret"` reflects the client's default `clientAuthenticatorType`
attribute, not secret-based authentication — `basetool-frontend` is a **public** client,
`publicClient: true`; the public→confidential migration is ADR-0001, implementation pending.) Because
the frontend is a **server-rendered Spring BFF** whose refresh token is held only in the Redis-backed
Spring Session and never reaches the browser, refresh-token rotation + reuse detection — whose purpose
is to bound the damage of a refresh token leaking from an *untrusted* client environment (browser /
SPA / native) — adds little here while being the **direct cause of the session revocations** under the
BFF's unavoidable concurrent-refresh race. The realm-wide control is therefore turned **off**
(`Revoke Refresh Token = Off`; realm-export `"revokeRefreshToken": false`): a replayed or duplicate
online refresh token is no longer treated as stale-token reuse, so the SSO session is not revoked and
the homepage no longer shows "Fehler beim Laden der Einsätze". The `SingleFlightAuthorizedClientManager`,
the structurally-refresh-free SSE relay and the `EXPIRY_SKEW ≥ 60s` invariant above are **retained as
defense-in-depth** but are no longer load-bearing. Consequence to weigh: rotation/reuse-detection no
longer protects the persisted desktop-extractor refresh token — the runbook already records this as a
reversible, ingest-independent operator lever (`INGEST_KEYCLOAK_SETUP.md`). See ADR-0019
(2026-06-18 amendment #4).

**2026-06-20 — a second, rotation-independent root cause: the `scope` request param leaking into
the grant.** Production still showed "Fehler beim Laden" after rotation was turned off, but the
Keycloak event was a different one: `REFRESH_TOKEN_ERROR error="invalid_request" reason="Invalid
scopes: all"` (and `"Invalid scopes: mine"`), surfacing in the frontend as
`ClientAuthorizationException [invalid_scope]` out of `RefreshTokenOAuth2AuthorizedClientProvider`.
The values `all` / `mine` are the job-orders **"Staffel" filter** (`orders-index.html`, radio
buttons `name="scope"` `value="mine|all"` — "Eigene Staffel" / "Alle Staffeln"), not OAuth scopes.
The cause is a Spring footgun: `DefaultOAuth2AuthorizedClientManager`'s default
`contextAttributesMapper` copies a request parameter literally named `scope` into
`OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME`, which the refresh provider then sends to
Keycloak as the requested scope of the **refresh-token grant**. So any token refresh that coincides
with a request carrying `?scope=all|mine` is rejected with `invalid_scope`, and the SSO session is
bounced into re-authentication — intermittently, because only a refresh that lands on such a request
is poisoned (a no-`scope` background poll refreshes cleanly, which is why a reload "after a bit"
recovers). The frontend never requests scopes dynamically (they are fixed on the `keycloak` client
registration), so the manager's `contextAttributesMapper` is overridden to return an empty map
(`WebClientConfig.NO_REQUEST_DERIVED_ATTRIBUTES`), severing the request-parameter → OAuth-scope path
entirely. This is orthogonal to the rotation/reuse-detection mitigation above and to single-flight;
it MUST hold regardless of either.

**Acceptance**

- [ ] A `client_authorization_required` on an HTML navigation redirects (302) to the Keycloak login
  flow instead of rendering an empty page / 500.
- [ ] The same failure on an AJAX call returns `401` with an `X-Reauthenticate` header and the
  client redirects the window.
- [ ] A burst of concurrent same-session authorize calls issues exactly one refresh-token grant,
  including when some callers lack the attached servlet request (session id recovered from context).
- [ ] The notification SSE relay never issues a refresh-token grant: it relays a read-only bearer as
  a plain `Authorization` header over a WebClient with no OAuth2 exchange filter (verbatim even when
  the token is already expired) and fails soft when no token is bound.
- [ ] `EXPIRY_SKEW` ≥ the refresh provider's clock skew (60s) so a freshness-cache hit never serves a
  token the provider would refresh.
- [ ] `ClientAuthorizationException` is not retried and does not open the backend circuit breaker.
- [ ] The `iri` realm has `Revoke Refresh Token = Off` (`"revokeRefreshToken": false`), so a replayed
  or duplicate online refresh token does not trip reuse detection and does not revoke the SSO session.
- [ ] An application request parameter named `scope` (e.g. the job-orders Staffel filter's
  `scope=all|mine`) never reaches Keycloak as the refresh-token grant scope: the
  `DefaultOAuth2AuthorizedClientManager` `contextAttributesMapper` returns an empty map, so a token
  refresh coinciding with such a request is not rejected with `invalid_scope`.

**Enforced by:** `SingleFlightAuthorizedClientManagerTest`, `OAuth2ScopeRequestParamLeakTest`,
`NotificationPageControllerStreamTest`, `GlobalExceptionHandlerTest`,
`BackendApiClientResilienceTest` · **Code:** `SingleFlightAuthorizedClientManager`, `WebClientConfig`,
`ReauthenticationRequiredException`, `BackendApiClient`, `GlobalExceptionHandler`,
`NotificationPageController`, `krt-fetch.js` · **Issues:** ingest-rollout
regression · **ADR:** ADR-0019

### REQ-SEC-013 — Frontend role checks read the Authentication token, not the OidcUser principal

Frontend membership/role predicates (e.g. the member-only mission finance/refinery gate) MUST read
the request `Authentication`'s authorities — the same source `sec:authorize` and `@PreAuthorize`
consult — via `FrontendAuthHelperService`, never the `@AuthenticationPrincipal OidcUser`'s own
`getAuthorities()`. Spring's `userAuthoritiesMapper` maps the Keycloak `realm_access.roles` onto the
`OAuth2AuthenticationToken`, not onto the `OidcUser` principal object, so a check that reads the
principal sees none of the mapped `ROLE_*` unless `BackendRoleSyncFilter` happened to rebuild the
principal that session. The mission-detail finance gate (`MissionPageController.isMemberOrAbove`)
regressed exactly this way: it read the principal, so the "Finanzen" panel silently collapsed
(database rows intact, backend returning `200`) for any session whose one-shot role sync had been
skipped, while the panel chrome still rendered because the template's `sec:authorize` correctly read
the token.

`BackendRoleSyncFilter` (which enriches the principal with backend-DB roles/permissions) MUST mark a
session synced (`BACKEND_ROLES_SYNCED`) only when the `/api/v1/users/me` read genuinely succeeded. A
Resilience4j fallback (`null`, no exception) or a thrown error MUST leave the flag unset so the next
request retries, rather than poisoning the whole session with an under-privileged principal until the
user re-logs in.

**Acceptance**

- [ ] `FrontendAuthHelperService.isMemberOrAbove()` is true for any member/elevated `ROLE_*` on the
  Authentication token and false for anonymous, missing-context and role-less GUEST callers.
- [ ] A member's `GET /missions/{id}` triggers the member-only finance-entries fetch; an anonymous
  visitor's does not.
- [ ] `BackendRoleSyncFilter` does not set `BACKEND_ROLES_SYNCED` when `/api/v1/users/me` returns
  `null` or throws; it does set it when the read succeeds.

**Enforced by:** `FrontendAuthHelperServiceTest`, `BackendRoleSyncFilterTest`,
`MissionPageControllerMvcTest` · **Code:** `FrontendAuthHelperService`, `MissionPageController`,
`BackendRoleSyncFilter` · **Issues:** mission-finance-panel regression

### REQ-SEC-014 — Encrypted transport to Keycloak (no cleartext edge)

Production Keycloak MUST serve **HTTPS only** — `--http-enabled=false --https-port=18443`, with the
shared bind-mounted `keystore.p12` — so neither edge that reaches it is cleartext:

- **NPM &rarr; Keycloak:** `nginx-proxy-manager` terminates the public Let's Encrypt cert and
  re-encrypts to `https://keycloak:18443` (nginx does not verify the self-signed upstream cert).
- **backend &rarr; Keycloak (admin/user-sync):** `KeycloakService` calls `https://keycloak:18443`
  directly over the isolated `net-backend-keycloak` network, pinning the self-signed cert via the
  `keycloak-trust` Spring SSL bundle (mirrors the frontend/ingest `backend-trust` approach, audit
  finding M-13). Unlike those reactive WebClients, the synchronous JDK `HttpClient` keeps **hostname
  verification ON** (it cannot be disabled reliably per-client), so the cert's SAN MUST include
  `dns:keycloak`.

The management/health interface (port 9000) is exempt: it stays HTTP via
`--http-management-scheme=http` because the Quarkus image ships no TLS-capable CLI client for the
container healthcheck, and the port is container-loopback only (never published, never on a proxy
network). Dev/test are exempt (Keycloak stays HTTP; the admin URL is plain HTTP and no
`keycloak-trust` bundle is defined, so `KeycloakService` falls back to the default client).

**Acceptance**

- [ ] In prod, Keycloak exposes no plain-HTTP listener; the public host works through NPM over TLS.
- [ ] The backend user sync succeeds against `https://keycloak:18443` with hostname verification on
  (cert SAN carries `dns:keycloak`).
- [ ] Keycloak reports `healthy` (the HTTP management healthcheck still passes after the HTTPS flip).
- [ ] With no `keycloak-trust` bundle (dev/test), `KeycloakService` builds and talks plain HTTP.

**Enforced by:** `KeycloakServiceTest` · **Code:** `KeycloakService`, `application-prod.yml`
(`spring.ssl.bundle.jks.keycloak-trust`), `docker-compose.yml` (`keycloak` command, backend
`KEYCLOAK_ADMIN_URL`) · **Runbook:** [`deployment.md` &rarr; Keycloak behind NPM over HTTPS](../deployment.md#keycloak-behind-npm-over-https)

### REQ-SEC-015 — Bereich/OL leadership grants officer-equivalent reach, never admin rights

The org hierarchy (REQ-ORG-014) introduces Bereich and OL leadership whose access **cascades** down to
subordinate units (REQ-ORG-015). This is a security-load-bearing carve-out, so the invariant is pinned
here:

- A Bereich/OL leadership principal gets **officer-equivalent reach** over its scope as a **concrete
  `memberOrgUnitIds` union** (and matching contextual authorities) — it MUST **never** route through the
  `adminAllScope=true` branch and MUST **never satisfy `isAdmin()`**. Every `hasRole('ADMIN')` gate
  (admin area, SK lifecycle, system settings, stammdaten, promotion-topic guards, bank admin/audit) stays
  closed to it.
- **Strict silo:** a Bereichsleitung sees/edits only its own Bereich's descendants; only the OL crosses
  Bereiche. No peer-Bereich access, even read-only.
- **ArchUnit-whitelist obligation:** the name-keyed rules `staffelScopedServicesMustWireOwnerScopeOrAuthHelper`,
  `staffelScopedWriteEndpointsMustGateOnOwnerScopeService` and `orgUnitAwareBankSeamIsContainedToOneClass`
  silently skip classes not in their set; **every** new scoped controller/service added by the
  restructure MUST be added to the relevant whitelist in the same PR (or covered by an
  annotation/package-based rule), so no new write endpoint ships ungated.

**Acceptance**

- [x] An OL/Bereich principal fails `isAdmin()` and is rejected by every `hasRole('ADMIN')` gate.
- [x] A Bereichsleitung is denied another Bereich's data (lists and detail gates).
- [x] A new scoped controller/service is caught by the ArchUnit scope/whitelist rules (a deliberately
  ungated one fails the build).

**Enforced by:** `OwnerScopeServiceTest` (`CascadingScopeTests`: `cascade_neverSetsAdminAllScope`,
strict-silo foreign-unit denial, OL concrete-union) and `OrgUnitCascadeServiceTest`;
`ArchitectureTest` — `cascadeServiceMustNotConsultTheSecurityContext` (the cascade can never branch on
admin status, so it can never grant admin), the `staffelScopedServicesMustWireOwnerScopeOrAuthHelper`
whitelist (incl. the new `OrgUnitBankAccessService`) and `staffelScopedWriteEndpointsMustGateOnOwnerScopeService`
(a new ungated scoped endpoint fails the build); and the `OrgHierarchyVisibilityMatrixE2eTest` cross-Bereich
matrix on the ephemeral stack (Phase 7, `e2e`-label-gated) · **ADR:**
[ADR-0026](../adr/0026-cascading-scope-without-admin.md) · **Issues:** #692, #696, #700.

## Out of scope

OrgUnit scoping/visibility rules (see [`org-unit-tenancy.md`](org-unit-tenancy.md)); the
confidential-client migration decision (see ADR-0001).
