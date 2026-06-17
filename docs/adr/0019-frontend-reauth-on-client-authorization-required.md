# ADR-0019 — Frontend recovery from `client_authorization_required` + single-flight token refresh

- **Status:** Accepted
- **Date:** 2026-06-17
- **Deciders:** @greluc
- **Related:** spec REQ-SEC-012 · ADR-0001 (frontend OAuth2 client) · runbook `docs/INGEST_KEYCLOAK_SETUP.md` (step 4)

## Context

The frontend is an OAuth2 login client for the `keycloak` registration and relays the user's
bearer token to the backend via WebClient. It deliberately does **not** request `offline_access`
(audit finding L-4), so it holds only an online refresh token bound to the Keycloak SSO session,
and the access-token lifespan is 5 minutes.

As part of the desktop-ingest rollout, Keycloak realm-wide refresh-token rotation with reuse
detection was enabled (`Revoke Refresh Token = on`, `Refresh Token Max Reuse = 0`; see
`INGEST_KEYCLOAK_SETUP.md` step 4). A logged-in page fans out several backend-bound requests that
share one Spring session but run on separate servlet threads — the page render, the notification
SSE relay (`/notifications/stream`) and the periodic unread-count poll. When the 5-minute access
token expires, two or more of those requests independently ask
`DefaultOAuth2AuthorizedClientManager` to refresh and each replays the **same** stored refresh
token. The first refresh rotates it; the racing second replays the now-consumed token; reuse
detection revokes the whole token family. From then on every call throws
`ClientAuthorizationException [client_authorization_required]` until the user logs in again.

Two failures compound the user impact:

1. The exception is a `RuntimeException`, not an `AuthenticationException`, so it never reaches
   `SsoReAuthenticationEntryPoint` — the silent-reauth entry point does not fire. Pages render
   empty (or a 500) with no redirect.
2. `BackendApiClient` mapped the unrecognised exception through its catch-all, logging a full
   stack trace per failed call (a log flood) and surfacing a generic 500.

## Decision

We will make the frontend **recover gracefully** from `client_authorization_required` and
**prevent the in-session refresh race** that causes it, without re-adding `offline_access`:

1. **Single-flight refresh.** Wrap the `OAuth2AuthorizedClientManager` in a
   `SingleFlightAuthorizedClientManager` that serialises refreshes per session (striped lock,
   keyed by session id) and serves a short-lived freshness cache, so the parallel fan-out issues
   exactly **one** refresh-token grant per expiry window.
2. **Typed re-auth signal.** `BackendApiClient` detects a `ClientAuthorizationException` in the
   cause chain and rethrows a `ReauthenticationRequiredException` (logged at DEBUG, no stack trace)
   instead of a generic 500.
3. **Central recovery.** `GlobalExceptionHandler` maps that exception to an interactive re-login:
   a `302` to `/oauth2/authorization/keycloak` for HTML navigations, or a `401` carrying the
   `X-Reauthenticate` header for AJAX callers so the shared `krtFetch`/`krtReauth` client redirects
   the window. The notification SSE relay pushes a `reauth` event for the same purpose.
4. **Resilience tuning.** `ClientAuthorizationException` is added to the Resilience4j
   `ignoreExceptions` so it is neither retried nor counted toward the circuit breaker (it is
   per-session auth state, not backend health).

The operator-side mitigation — setting `Refresh Token Max Reuse` to a small value > 0 — remains the
recommended robust fix for multi-instance deployments and is documented in the runbook; the code
changes here make a single frontend instance correct on their own and degrade safely to that
operator setting when scaled horizontally.

## Consequences

- The `client_authorization_required` flood stops: at most one refresh per session per expiry
  window, no retried auth failures, no stack-trace spam, and a self-healing redirect to a fresh
  (typically silent) Keycloak login.
- The single-flight cache holds tokens in JVM memory (already present in Redis) for the access-token
  lifetime; it is bounded and session-keyed. Cache-hit requests rely on Spring Session delta-saves
  not rewriting the authorized-client attribute, so they cannot clobber the refreshed token in
  Redis.
- Single-flight is JVM-local: with the frontend scaled to multiple instances, a cross-instance race
  is still possible and is covered by `Refresh Token Max Reuse > 0` on Keycloak.
- A client-side `sessionStorage` guard rate-limits re-auth redirects to avoid a loop if a freshly
  minted session were revoked again immediately.

## Alternatives considered

- **Re-add `offline_access`** — rejected: audit finding L-4 removed it deliberately; a long-lived
  offline token broadens the blast radius of a leaked session and does not address the rotation
  race.
- **Disable refresh-token rotation entirely** — rejected as the *code* fix: rotation/reuse
  detection is a genuine security control (valuable for the persisted desktop-extractor token). The
  runbook keeps turning it off / raising max-reuse as a reversible operator lever, but the frontend
  should not depend on it.
- **Naive per-session lock with no cache** — rejected: a lock alone does not help, because the
  second request re-reads its own request-scoped (stale) session snapshot and refreshes again. The
  freshness cache is what actually collapses the refreshes.
- **Treat it as an `AuthenticationException` to reuse `SsoReAuthenticationEntryPoint`** — rejected:
  it would require wrapping/translating inside the security filter chain for an error that is raised
  deep in the WebClient exchange on a Reactor worker thread; handling it at the MVC boundary is
  simpler and also covers the AJAX/SSE transports the entry point does not.

