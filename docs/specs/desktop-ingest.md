> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-16.
> **Owner area:** INGEST · **Related ADRs:** [ADR-0018](../adr/0018-desktop-ingest-gateway-device-grant.md) · **Related:** epic [#639](https://github.com/krt-iri/basetool/issues/639), runbook [`INGEST_KEYCLOAK_SETUP.md`](../INGEST_KEYCLOAK_SETUP.md), [`refinery-screenshot-import.md`](refinery-screenshot-import.md) (`REQ-REFINERY-018`), [`security-and-access.md`](security-and-access.md), [`api-conventions.md`](api-conventions.md), [ADR-0007](../adr/0007-client-side-vlm-screenshot-extraction.md), [ADR-0008](../adr/0008-refinery-extract-json-contract.md)

# Desktop one-click ingest (send-to-basetool)

## Context & goal

The desktop extractor (`basetool-bp-extractor`) produces refinery-extract and
personal-blueprint JSON entirely on the user's machine. This spec governs the path that
lets the user send that JSON into the basetool with **one click** at the end of the
extractor workflow — landing on the matching basetool page with the data already
pre-filled, logging in first if there is no session — instead of saving a file and
uploading it by hand.

The transport, auth and handoff design — a dedicated minimal-surface `ingest` gateway, a
Keycloak Device-Authorization public client, and a short-lived single-use Redis handoff —
is recorded in [ADR-0018](../adr/0018-desktop-ingest-gateway-device-grant.md). This spec
holds the binding requirements that design must satisfy. It is the #640 decision and
requirements gate of epic [#639](https://github.com/krt-iri/basetool/issues/639), the
first of sub-issues #640–#648 to land.

The non-negotiable property: **the one-click path may pre-fill, never persist.** Squadron
data is only written when the user reviews the pre-filled draft and saves it through the
unchanged create path. Direct ingest is an alternative *transport* for the import draft,
not a new write path.

## Requirements

### REQ-INGEST-001 — Dedicated gateway, minimal forward-only surface

A new standalone service (the `ingest` gateway) is the only new internet-reachable
surface. It exposes **exactly two** endpoints, one per existing import draft:
refinery-extract and blueprint-preview. Each endpoint validates the caller's JWT, forwards
the **same bearer** to the corresponding internal backend import endpoint
(`POST /api/v1/refinery-orders/import-extract`, `POST /api/v1/personal-blueprints/import/preview`),
stages the returned draft for browser pickup, and returns a handoff id. The gateway has
**no database and no Flyway migration**, serves **no HTML**, holds **no business logic**
(matching/validation stay backend-side, ADR-0008), and persists **nothing** durable of its
own. The backend remains internet-unreachable — the gateway reaches it over the internal
network only.

**Acceptance**

- [ ] The gateway exposes only the two documented ingest endpoints plus the actuator
  health endpoint; every other path is 404/401.
- [ ] An ingest call results in exactly one forwarded call to the matching backend import
  endpoint, carrying the caller's bearer, and no backend write.
- [ ] The gateway declares no `DataSource`/JPA and runs no schema migration (architecture
  test / startup assertion).

**Enforced by:** _(pending — #642)_ · **Code:** _(new `ingest` module — #642)_ · **Issues:** #642

### REQ-INGEST-002 — Authentication & authorization

The gateway is a Keycloak JWT resource server in the existing realm. Tokens are issued to a
new **public** Keycloak client (`basetool-sc-extractor`) via the **Device Authorization
Grant** (RFC 8628) with PKCE and **no client secret**. The gateway requires
`isAuthenticated()` — no elevated role; any member may ingest, mirroring `REQ-REFINERY-011`.
The token carries `aud=basetool-backend` (stamped by the dedicated `extractor-ingest` client
scope, #641); the **same** bearer is forwarded to and accepted by the backend. No separate
ingest audience is provisioned — the gateway only relays to the backend, so it accepts the
same `basetool-backend` audience the backend requires. All data is scoped to the token's
`sub`; the gateway never acts for a different user.

**Acceptance**

- [ ] A request without a valid signed realm token (carrying `aud=basetool-backend`) is
  rejected 401/403; no forward happens.
- [ ] The device-grant client is public (no secret) and the secret is never embedded in the
  desktop binary or in any committed config.
- [ ] The handoff staged by an ingest call is readable only under the same `sub`.

**Enforced by:** _(pending — #642/#647)_ · **Code:** _(ingest module security config — #642; Keycloak client — #641)_ · **Issues:** #641, #642

### REQ-INGEST-003 — Short-lived single-use Redis handoff

The non-persisted draft returned by the backend is staged in Redis under a key derived from
`(sub, handoffId)`. The `handoffId` is cryptographically unguessable (≥ 128 bits of
entropy). The entry has a short TTL (~5 minutes) and is **single-use**: the first successful
read for the correct `sub` consumes (deletes) it. A second read, a wrong `sub`, an expired
entry, or an unknown id all return "not found" with no draft. No screenshots and no raw
image bytes are ever staged — only the already-matched draft DTO (ADR-0007/0008: images
never leave the machine).

**Acceptance**

- [ ] A handoff id is unguessable and bound to the creating `sub`; reading under another
  `sub` returns not-found.
- [ ] A second read of the same id after a successful first read returns not-found
  (single-use).
- [ ] An entry past its TTL is gone; no draft is returned and no error leaks its prior
  existence.

**Enforced by:** _(pending — #642)_ · **Code:** _(ingest Redis handoff service — #642)_ · **Issues:** #642

### REQ-INGEST-004 — Browser pre-fill, review-before-commit preserved

The extractor opens the matching basetool page with `?handoff=<id>`
(`/refinery-orders/create?handoff=<id>` and the blueprint equivalent). If the user has no
frontend session, the existing OAuth2 login + saved-request replay returns them to that URL
after authenticating. The frontend reads the staged draft for `(session sub, handoffId)`
exactly once and pre-fills the **existing** review form (REQ-REFINERY-014/-015 for
refinery; the blueprint preview surface for blueprints). Saving goes **exclusively** through
the unchanged create path — the ingest path adds no new persistence and does not alter the
create flow. A missing, expired, consumed, or foreign-`sub` handoff degrades to the normal
empty create form plus a localized, KRT-styled inline notice (no native dialog,
REQ-UI-008); it never errors the page out.

**Acceptance**

- [ ] Opening `…/create?handoff=<valid id>` while logged in renders the pre-filled review
  form; the user must still click Save to persist.
- [ ] Opening it without a session triggers login and lands back on the pre-filled form.
- [ ] An expired/consumed/foreign/unknown handoff renders the normal empty form with an
  inline notice — no stack trace, no persisted data.

**Enforced by:** _(pending — #644/#647 e2e)_ · **Code:** _(frontend create-page controllers — #644)_ · **Issues:** #644

### REQ-INGEST-005 — Size and rate limits

The gateway caps each ingest payload at the same ceiling the existing frontend proxy uses
(2 MB — a real extract is a few KB) and rejects larger bodies before forwarding. Ingest
calls are rate-limited per `sub` (and per source IP) so the new ingress cannot be used to
hammer the backend import endpoints. Defensive payload caps inherited from the backend DTOs
(`REQ-REFINERY-001` envelope limits) still apply at the backend; the gateway does not relax
them.

**Acceptance**

- [ ] A body over the size cap is rejected by the gateway with a localized problem response
  and is never forwarded.
- [ ] A burst of ingest calls from one `sub`/IP is throttled, not passed straight through.

**Enforced by:** _(pending — #642)_ · **Code:** _(ingest gateway limits — #642)_ · **Issues:** #642

### REQ-INGEST-006 — Egress is opt-in; the CLI stays offline

Data leaves the user's machine **only** when the user explicitly clicks Send in the
extractor GUI. There is no background sync, no auto-send, and no telemetry. The extractor's
CLI / offline mode never transmits. The "nothing leaves your machine" promise in the
extractor's documentation is reconciled to state precisely that the locally-produced JSON is
transmitted to the basetool **only on an explicit Send**, and that screenshots/images never
leave the machine (ADR-0007).

**Acceptance**

- [ ] No extractor code path transmits the extract without an explicit user Send action.
- [ ] The CLI path performs no network egress of extract data.
- [ ] The extractor docs describe the egress accurately (no remaining absolute
  "nothing-leaves" claim).

**Enforced by:** _(pending — extractor repo tests #645)_ · **Code:** _(extractor send action — #645; docs — #646)_ · **Issues:** #645, #646

### REQ-INGEST-007 — "Remember me" token storage & revocation

If the user opts into "remember me", the extractor persists the device-grant **refresh
token** in the Windows Credential Manager (DPAPI) — never in plaintext on disk, never in a
log. Refresh-token rotation is used with reuse-detection (a replayed old refresh token
invalidates the session). The extractor offers an in-app "Vom Basetool trennen" action that
revokes the token at Keycloak and clears the stored credential. Tokens, refresh tokens and
the user's name/email are never logged (project-wide logging rule).

**Acceptance**

- [ ] With "remember me" off, no refresh token is persisted; a new send re-runs the device
  approval.
- [ ] With it on, the refresh token is stored via DPAPI and a second send needs no
  re-approval until expiry/revocation.
- [ ] "Vom Basetool trennen" revokes at Keycloak and removes the stored credential; a
  subsequent send requires re-approval.
- [ ] No token or refresh token appears in any log line.

**Enforced by:** _(pending — extractor repo tests #648)_ · **Code:** _(extractor token store — #648)_ · **Issues:** #648

### REQ-INGEST-008 — No new role; backend stays internal; audience sequencing

Direct ingest introduces **no new Keycloak role or Spring authority** — it is
`isAuthenticated()` end to end (ROLES_AND_PERMISSIONS.md unchanged). The backend remains
internet-unreachable; only the gateway is published. If/when the backend's opt-in audience
check (`app.security.jwt.expected-audiences`) is enabled, the `aud=basetool-backend` audience
mapper must already be emitting on **both** token sets — the new client's `extractor-ingest`
scope **and** the existing frontend client's scope — or enabling it rejects every frontend
token. Adding the mappers (and verifying both token sets carry the claim) must therefore
precede turning the check on, in that order.

**Acceptance**

- [ ] No new role/authority appears in `ROLES_AND_PERMISSIONS.md` for ingest.
- [ ] Enabling `app.security.jwt.expected-audiences` is gated on both clients already
  emitting `aud=basetool-backend` (documented runbook step, #641).

**Enforced by:** _(pending — #641/#647)_ · **Code:** _(Keycloak realm config per [`INGEST_KEYCLOAK_SETUP.md`](../INGEST_KEYCLOAK_SETUP.md) — #641; backend `SecurityConfig` audience knob, already present)_ · **Issues:** #641

## Out of scope

- The desktop extractor's internals (device-flow UI, token store implementation) — they live
  in the `basetool-bp-extractor` repo; this spec governs the basetool-side contract and the
  cross-repo expectations (#645/#646/#648 track the extractor work).
- Server-to-server / unattended ingest (no user in the loop) — explicitly excluded: ingest is
  always tied to a `sub` and a browser review step. There is no `client_credentials` path
  (ADR-0018).
- New import *semantics*. Matching, validation, draft shape and the create path are
  unchanged (ADR-0008, `REQ-REFINERY-002`); ingest only changes how the draft request is
  delivered.

## Open questions

- Whether the blueprint preview endpoint stays multipart or gains a JSON sibling for the
  gateway to forward to (decided in #642). Either way the gateway forwards, it does not
  reshape the contract.
- Final hostname / NPM proxy entry and CI deployment shape (decided in #643).

