# ADR-0018 — Desktop one-click ingest: dedicated gateway + Keycloak device grant

- **Status:** Accepted — implementation pending (epic [#639](https://github.com/krt-iri/basetool/issues/639))
- **Date:** 2026-06-16
- **Deciders:** Lucas Greuloch (@greluc)
- **Related:** epic [#639](https://github.com/krt-iri/basetool/issues/639) · spec [`desktop-ingest.md`](../specs/desktop-ingest.md) (`REQ-INGEST-*`) · [`refinery-screenshot-import.md`](../specs/refinery-screenshot-import.md) `REQ-REFINERY-018` · [ADR-0007](0007-client-side-vlm-screenshot-extraction.md) · [ADR-0008](0008-refinery-extract-json-contract.md) · supersedes the deferred direct-upload phase [#437](https://github.com/krt-iri/basetool/issues/437)

## Context

The desktop extractor (`basetool-bp-extractor`, ADR-0007) produces, fully on the user's
machine, two JSON documents the basetool already knows how to import: the
`RefineryExtract` (frozen contract v1, ADR-0008 → `POST /api/v1/refinery-orders/import-extract`)
and the personal-blueprint export (→ `POST /api/v1/personal-blueprints/import/preview`).
Today the **only** way that JSON reaches the basetool is a manual file dance: the user
saves the JSON to disk, opens the basetool in a browser, clicks the upload control, and
picks the file (REQ-REFINERY-013). ADR-0007 explicitly deferred a "Phase 4" direct upload
([#437](https://github.com/krt-iri/basetool/issues/437)).

The owner's goal is a single button at the end of the extractor workflow that opens the
matching basetool page **with the data already pre-filled** — logging the user in first if
there is no session, then landing on the pre-filled review form. The forces that constrain
how that can be built:

- **The backend is not reachable from the internet.** In the production topology
  (`docker-compose.yml`) only `frontend` and `keycloak` sit on the `nginx-proxy-manager`
  network and are published on 80/443; `backend` lives on internal networks
  (`net-db-backend`, `net-backend-frontend`, `net-backend-keycloak`) only. In dev it binds
  `127.0.0.1:11261`. A desktop app on a user's machine therefore **cannot** call the
  backend's import endpoints directly. This is a deliberate, load-bearing security property
  — not an accident to be worked around by exposing the backend.
- **No new attack surface.** The owner's explicit constraint ("achte besonders auf die
  Sicherheit um keine neuen Angriffsflächen zu öffnen"). Whatever ingress is added must be
  minimal, auditable, and must not weaken the existing posture (stateless JWT resource
  server, empty CORS with `allowCredentials=false`, no browser-to-backend traffic).
- **Review-before-commit is the binding safety control.** `REQ-REFINERY-002` and the
  blueprint preview flow both persist **nothing** on import; the user reviews a draft and
  saves through the unchanged create path. Any one-click path must preserve this — a click
  in the extractor may *pre-fill* a form but must never *persist* squadron data
  unattended.
- **Native apps cannot keep a client secret.** A desktop binary on a user's machine is a
  public OAuth2 client; any embedded secret is extractable. Whatever auth is chosen must
  work without a confidential secret.

## Decision

We will add a **dedicated, minimal-surface `ingest` gateway** as a third Spring Boot
service, and authenticate the desktop app with the **OAuth2 Device Authorization Grant**
(RFC 8628) using a new **public** Keycloak client (PKCE, no secret). The data reaches the
browser through a **short-lived, single-use Redis handoff**, and the existing review form
does the pre-fill. Concretely:

1. **Gateway module (transport "C3").** A new Spring Boot service on the proxy network with
   its own `nginx-proxy-manager` host (e.g. `ingest.profit-base.online`). It is a JWT
   resource server validating Keycloak tokens with audience `basetool-ingest`. It exposes
   **exactly two forward-only endpoints** — one per existing import (refinery-extract,
   blueprint-preview). Each endpoint: validates the bearer, **forwards the same bearer** to
   the corresponding internal backend import endpoint over the internal network, stages the
   returned non-persisted draft in Redis keyed `(sub, handoffId)` with a single-use ~5-minute
   TTL, and returns the unguessable `handoffId`. The gateway has **no database, no Flyway
   migration, serves no HTML, and persists nothing of its own.** The backend stays
   internet-unreachable; only this tiny new surface is published.

2. **Auth = Device Authorization Grant, public client.** A new public Keycloak client
   (`basetool-ingest-desktop`) with PKCE and no secret, device-grant enabled. The extractor
   runs the device flow; under an existing browser SSO session the user approves with one
   click. The token's audience includes `basetool-ingest` (gateway) — and, when the backend
   audience check is later enabled, `basetool-backend` so the forwarded bearer is accepted.

3. **Browser handoff via the existing frontend + review form.** The extractor opens
   `https://<frontend>/refinery-orders/create?handoff=<id>` (and the blueprint equivalent).
   If the user has no frontend session, the existing OAuth2 login + saved-request replay
   carries them back to that URL. The frontend reads the staged draft for `(session sub,
   handoffId)` once (consuming it) and pre-fills the **existing** review form. The user
   reviews and saves through the **unchanged** create path. Review-before-commit is
   preserved end to end.

4. **Persisted "remember me".** The extractor may persist the device-grant refresh token in
   the Windows Credential Manager (DPAPI), with refresh-token rotation and reuse-detection,
   so the second send is one click without re-approval. An in-app "Vom Basetool trennen"
   action revokes the token and clears the stored credential.

## Consequences

- **The backend never becomes internet-reachable.** The only new published surface is the
  gateway, whose attack surface is deliberately tiny: two endpoints, forward-only, no DB, no
  HTML, JWT-gated, per-`sub`. It cannot do anything a logged-in user could not already do
  through the browser upload — it just removes the file dance.
- **No DB/Flyway migration.** Handoff state lives in Redis (already in the stack for Spring
  Session) with a short TTL; nothing durable is added.
- **A new public Keycloak client and a new ingress are added** — both must be threat-modelled
  in the security-review sub-issue (#647). Mitigations: PKCE; single-use + short-TTL handoff
  with an unguessable id; per-`sub` scoping of both the forward and the handoff read; size
  and rate limits mirrored from the existing frontend proxy; refresh-token rotation +
  reuse-detection for remember-me.
- **Audience-validator sequencing trap.** The backend's `app.security.jwt.expected-audiences`
  check is currently off. Before it is ever switched on, the `aud=basetool-backend` mapper
  must be added to **both** the new ingest client **and** the existing frontend client —
  otherwise enabling the check rejects every frontend token and breaks the app. This
  ordering is a tracked task in the Keycloak sub-issue (#641).
- **Egress becomes opt-in, not automatic.** The extractor only ever transmits when the user
  clicks Send; the CLI / offline mode never transmits. The extractor's "nothing leaves your
  machine" documentation must be reconciled to "until you choose to send it" (#646).
- **`#437` is superseded.** The deferred Phase-4 direct-upload issue is continued/replaced by
  this epic; the refinery spec's out-of-scope note is updated accordingly
  (`REQ-REFINERY-018`).

## Alternatives considered

- **C1 — expose the backend directly.** Rejected: puts the entire authenticated API on the
  internet to serve one convenience feature, destroying the load-bearing
  "backend-is-internal" property. The blast radius of any future backend bug would expand
  from "reachable only via the SSR frontend" to "reachable by anyone".
- **C2 — add bearer/device auth to the existing frontend.** Rejected: the frontend is an
  OAuth2 *login client* (session cookies, SSR), not a resource server; bolting a second auth
  model and an API ingress onto it conflates two roles and loads the UI module with
  integration ballast. A separate gateway keeps each module single-purpose.
- **A — deep-link `?import=1` and let the user pick the file.** Rejected by the owner
  ("bringt nichts"): it is the same situation as today — the user still has to deal with the
  file and the filesystem. It does not move data, so it does not meet the one-click goal.
- **B — loopback HTTP server in the extractor + browser `fetch`.** Rejected: requires
  relaxing the frontend CSP `connect-src` to allow `http://127.0.0.1:*`, and depends on
  Private Network Access behaviour that browsers are actively tightening — fragile and a
  standing CSP hole.
- **Resource Owner Password Credentials (ROPC) grant.** Rejected: the desktop app would
  handle the user's Keycloak password directly; the grant is deprecated and incompatible with
  MFA/social login.
- **`client_credentials` grant.** Rejected: it carries no user identity, so per-`sub` data
  isolation (the whole basis of ownership and org-unit scoping) would be impossible.
- **A bespoke long-lived personal access token.** Rejected: Keycloak has no native PAT;
  building one means a custom long-lived-secret store with its own revocation and rotation
  machinery — strictly more attack surface than a rotating refresh token in the OS keystore.

