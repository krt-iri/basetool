# Desktop ingest — Keycloak setup runbook

> **Doc type:** Implementation runbook for [ADR-0018](adr/0018-desktop-ingest-gateway-device-grant.md)
> and [`docs/specs/desktop-ingest.md`](specs/desktop-ingest.md) (`REQ-INGEST-002`,
> `REQ-INGEST-007`, `REQ-INGEST-008`). The *decision* and the *requirements* live there; this
> document is the step-by-step *how* for the operator. Registered in
> [`docs/specs/INDEX.md`](specs/INDEX.md). Tracks GitHub issue #641 (epic #639).

**Status:** open — the live Keycloak changes and the validator enablement are an operator
deployment step that cannot be done by PR. The **prod realm is not in this repository**
(only the throwaway `frontend/src/e2e/resources/realm-export.e2e.json` is, and it is a test
artifact — do **not** copy its `directAccessGrantsEnabled: true`).

## What this sets up

The desktop extractor (`basetool-bp-extractor`) must obtain a **minimal, per-user,
audience-restricted** Keycloak token **without shipping any secret**, and that token must be
accepted by the backend's import endpoints (reached through the ingest gateway, #642). Four
pieces, applied in a **strict order**:

1. a new **public** client `basetool-sc-extractor` (device-grant, PKCE, no secret);
2. an **audience mapper** that stamps `aud=basetool-backend` on its access tokens (via a
   dedicated `extractor-ingest` client scope);
3. **the same audience mapper** on the existing frontend client, so the frontend's relayed
   user token also carries `aud=basetool-backend`;
4. **only then** the backend's opt-in audience validator turned on.

> **Critical sequencing (REQ-INGEST-008).** Enabling the backend audience validator before
> **both** the extractor token **and** the frontend token already carry `aud=basetool-backend`
> will reject the frontend's tokens and **break the entire app** (every page is behind the
> frontend's user token). Apply steps 1–3, **verify both token sets carry the claim**, and
> only then do step 4. Do the whole sequence in a **staging realm first**.

## Prerequisites

- Admin access to the Keycloak realm that backs prod (the same realm the frontend client
  `basetool-frontend` and the backend resource server live in).
- A staging/replica realm to rehearse the sequence.
- The ability to restart the backend container (step 4 is an env change).

## Step 1 — New public client `basetool-sc-extractor`

Realm → Clients → Create client. Settings (Admin Console fields → the equivalent
realm-export JSON keys):

|           Console setting            |                                Value                                 |
|--------------------------------------|----------------------------------------------------------------------|
| Client ID                            | `basetool-sc-extractor`                                              |
| Client authentication                | **Off** (public client — no secret)                                  |
| Standard flow                        | **On** (RFC 8252 loopback auth-code fallback)                        |
| Direct access grants                 | **Off** (no ROPC — the desktop app must never see the password)      |
| Service accounts                     | **Off**                                                              |
| OAuth 2.0 Device Authorization Grant | **On**                                                               |
| PKCE Code Challenge Method           | `S256` (required)                                                    |
| Valid redirect URIs                  | `http://127.0.0.1/*`, `http://localhost/*` (loopback only, RFC 8252) |
| Web origins                          | *(empty — no browser CORS surface)*                                  |

Equivalent realm-export fragment (for reference / IaC):

```json
{
  "clientId": "basetool-sc-extractor",
  "name": "Basetool SC Extractor (desktop)",
  "enabled": true,
  "protocol": "openid-connect",
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "fullScopeAllowed": false,
  "redirectUris": ["http://127.0.0.1/*", "http://localhost/*"],
  "webOrigins": [],
  "attributes": {
    "oauth2.device.authorization.grant.enabled": "true",
    "pkce.code.challenge.method": "S256"
  }
}
```

Notes:

- `publicClient: true` + no secret is correct and RFC-conform for a native app (a desktop
  binary cannot keep a secret — REQ-INGEST-002). PKCE `S256` is the proof-of-possession that
  replaces the secret.
- `fullScopeAllowed: false` keeps the token's roles to what the client scope grants, not the
  whole realm — least privilege.
- The device grant has no redirect; the redirect URIs only serve the loopback auth-code
  fallback.

## Step 2 — Audience mapper via an `extractor-ingest` client scope

Create a client scope and attach the audience mapper, then assign the scope to the new
client as a **default** scope (so every token it issues carries the audience).

Realm → Client scopes → Create client scope:

| Setting  |       Value        |
|----------|--------------------|
| Name     | `extractor-ingest` |
| Type     | Default            |
| Protocol | `openid-connect`   |

Add mapper → **Audience**:

|      Mapper setting      |         Value          |
|--------------------------|------------------------|
| Name                     | `aud-basetool-backend` |
| Included Custom Audience | `basetool-backend`     |
| Add to access token      | **On**                 |
| Add to ID token          | Off                    |

> If a Keycloak **client** named `basetool-backend` exists for the backend resource server,
> use *Included Client Audience* = `basetool-backend` instead of *Included Custom Audience*;
> both emit the identical `aud` value. The backend only checks the string value
> (`app.security.jwt.expected-audiences=basetool-backend`).

Then: Clients → `basetool-sc-extractor` → Client scopes → Add client scope → `extractor-ingest`
as **Default**.

Equivalent realm-export fragment:

```json
{
  "name": "extractor-ingest",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "true", "display.on.consent.screen": "false" },
  "protocolMappers": [
    {
      "name": "aud-basetool-backend",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-audience-mapper",
      "config": {
        "included.custom.audience": "basetool-backend",
        "access.token.claim": "true",
        "id.token.claim": "false"
      }
    }
  ]
}
```

## Step 3 — Same audience mapper on the frontend client

The frontend relays the **user's** access token to the backend (through the gateway for
ingest, directly for everything else). That token must also carry `aud=basetool-backend`, or
step 4 breaks it.

Either assign the same `extractor-ingest` scope to `basetool-frontend` as a default scope,
**or** (cleaner separation) add an identical Audience mapper to the frontend client's own
dedicated scope. Whichever you pick, the result must be: a fresh `basetool-frontend` login
token contains `"aud": [..., "basetool-backend"]`.

## Step 4 — Refresh-token hardening (for "remember me", REQ-INGEST-007)

Realm settings → Tokens (realm-level — note this affects the whole realm):

|         Setting         |         Value          |                                        Why                                        |
|-------------------------|------------------------|-----------------------------------------------------------------------------------|
| Revoke Refresh Token    | **On**                 | rotation: each refresh issues a new refresh token                                 |
| Refresh Token Max Reuse | `0`                    | reuse-detection: a replayed old refresh token is rejected and the session revoked |
| Access Token Lifespan   | realm default (~5 min) | keep short; the desktop app refreshes                                             |

Equivalent realm-export keys: `"revokeRefreshToken": true`, `"refreshTokenMaxReuse": 0`.

> This is the realm-wide setting that makes the persisted desktop refresh token safe to keep
> in the OS keystore: a stolen-and-replayed token is detected and kills the session.

## Step 5 — VERIFY both token sets carry the audience (gate for step 6)

Do **not** proceed to step 6 until both checks pass.

1. **Extractor token:** run the device flow against `basetool-sc-extractor` (or use the
   extractor's send action once #645 ships), then decode the access token (jwt.io / `jq`)
   and confirm `aud` contains `basetool-backend`.
2. **Frontend token:** log into the frontend normally, capture its access token (server log
   at debug, or a fresh login in staging), decode it, and confirm `aud` contains
   `basetool-backend`.

If either token is missing the claim, fix the mapper/scope assignment (steps 2–3) and
re-verify. Enabling step 4's validator while either token lacks the claim is the documented
break.

## Step 6 — Enable the backend audience validator

Only after step 5 passes on **both** tokens:

```properties
# backend env (docker-compose / .env)
APP_SECURITY_JWT_EXPECTED_AUDIENCES=basetool-backend
```

This sets `app.security.jwt.expected-audiences`, which the backend's already-present
`@ConditionalOnProperty` decoder (`SecurityConfig.audienceValidatingJwtDecoder` /
`audienceValidator`) activates — layering an `aud` check on top of the existing signature /
issuer / expiry validation. Restart the backend. Smoke-test: the frontend still works
(pages load, writes succeed) **and** an extractor ingest call still reaches the backend.

## Rollback

- **Step 6:** unset `APP_SECURITY_JWT_EXPECTED_AUDIENCES` and restart the backend — the
  validator becomes inert (the decoder bean is no longer created); all previously-valid
  tokens are accepted again. This is the fast rollback if anything 401s after step 6.
- **Steps 1–3:** remove the `extractor-ingest` scope assignment / the `basetool-sc-extractor`
  client. Harmless to leave in place even if the gateway is not yet deployed — the client
  issues tokens nobody consumes until #642 is live.
- **Step 4:** refresh-token rotation can be turned back off, but leaving it on is the more
  secure default and is independent of ingest.

## Security checklist (REQ-INGEST-002 / -007 / -008)

- [ ] `basetool-sc-extractor` is **public**, has **no secret**, ROPC **off**, service
  accounts **off**, web origins **empty**.
- [ ] PKCE `S256` required; redirect URIs are loopback only.
- [ ] `aud=basetool-backend` verified on **both** the extractor token and the frontend token
  **before** the validator is enabled.
- [ ] Refresh-token rotation + reuse-detection on.
- [ ] No client secret, refresh token, or user name/email is written to any config file or
  log (project-wide logging rule).

