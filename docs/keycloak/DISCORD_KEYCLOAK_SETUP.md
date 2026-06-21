# Discord login — Keycloak & Discord setup runbook

> **Doc type:** Runbook — operator deployment steps for epic #720, Track 1.
> **Scope:** Discord **login** with a fail-closed Kartell-membership gate. **OAuth only — no bot.**
> The Discord **bot** (and the automated role-sync) belong to Track 2 and are set up separately later.
> Spec: [`docs/specs/discord-integration.md`](../specs/discord-integration.md) · decision:
> [ADR-0030](../adr/0030-discord-federation-first-login-membership-gate.md).

This is an **operator** procedure: it provisions secrets and live Keycloak config that are **never**
committed. The redacted shape of the Keycloak side is in
[`realm-config.reference.json`](realm-config.reference.json) (the `identityProviders` entry and the
`basetool-frontend` `discord_user_id` mapper).

---

## 0. What you will need

|                   Value                   |                                                              Where it comes from                                                              |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Discord **Client ID** + **Client Secret** | the Discord application (step 1)                                                                                                              |
| Discord redirect URI                      | `https://<keycloak-host>/realms/iri/broker/discord/endpoint` (prod: `https://keycloak.profit-base.online/realms/iri/broker/discord/endpoint`) |
| **Guild ID** of the das-kartell server    | Discord client → Developer Mode → right-click the server → *Copy Server ID*                                                                   |
| **KRT-Mitglied role ID**                  | Server Settings → Roles → right-click *KRT-Mitglied* → *Copy Role ID* (numeric, **not** the name)                                             |

Enable **Developer Mode** in Discord (User Settings → Advanced) to get the *Copy ID* options.

---

## 1. Create the Discord application (OAuth2 only)

1. <https://discord.com/developers/applications> → **New Application** → name it (e.g. "Basetool Login").
2. **OAuth2** tab → copy the **Client ID** and **Client Secret** (reset the secret to reveal it).
3. **OAuth2 → Redirects** → add exactly the Keycloak broker endpoint from the table above. Save.
4. No bot, no privileged intents, no scopes selected on this screen — Keycloak requests the scopes.
   (The bot + `Server Members` intent are a **Track 2** step; do not enable them now.)

---

## 2. Build & stage the provider JAR

The Discord identity provider and the membership gate ship as one JAR from the `keycloak-spi` module.

```bash
./gradlew :keycloak-spi:build
# Output: keycloak-spi/build/libs/keycloak-spi-<version>.jar
```

Stage it into the providers volume on the Keycloak host (bind-mounted at `/opt/keycloak/providers`,
see `docker-compose.yml`):

```bash
sudo install -D -m 0644 keycloak-spi/build/libs/keycloak-spi-*.jar \
    /var/iri/code/keycloak/providers/keycloak-spi.jar
# 0644 is REQUIRED: the quay Keycloak image runs as uid 1000 and must read the JAR
# (the same world-readable lesson as the shared keystore.p12).
```

Restart Keycloak so the `start` command re-runs the provider build and discovers the JAR:

```bash
docker compose --profile prod up -d --no-deps keycloak
# Then confirm the provider loaded — "Discord" appears under Identity Providers → Add provider → Social.
```

---

## 3. Add the Discord identity provider in Keycloak

Realm `iri` → **Identity providers** → **Add provider** → **Social → Discord**.

- **Alias:** `discord` (must match — it is the `kc_idp_hint` value and the broker redirect path).
- **Client ID / Client Secret:** from step 1.
- **Default Scopes:** `identify email guilds.members.read` (the gate needs `guilds.members.read`).
- **Store Tokens:** **ON** (the gate reuses the stored Discord access token).
- **Trust Email:** optional; leave OFF unless you accept Discord's `verified` flag.
- **Hide on login page:** leave **OFF** (the default). Keycloak lists only non-hidden IdPs in the
  login page's social block, so this is what makes the **Discord** button appear on the Keycloak login
  form itself — reachable from the extractor's device-grant login and any direct login, not just the
  app-sidebar shortcut (the krt-theme renders the block; see §5).
- Save.

### 3a. Attribute importer mapper (the `discord_user_id` auto-link)

On the new Discord IdP → **Mappers** → **Add mapper**:

- **Name:** `discord-id`
- **Mapper type:** *Attribute Importer* (the Discord user-attribute mapper from the SPI).
- **Social Profile JSON Field Path:** `id`
- **User Attribute Name:** `discord_user_id`

### 3b. Carry the link into the token

Realm `iri` → **Clients → `basetool-frontend` → Client scopes →
`basetool-frontend-dedicated` → Add mapper → By configuration → User Attribute**:

- **Name:** `discord_user_id`
- **User Attribute:** `discord_user_id`
- **Token Claim Name:** `discord_user_id`
- **Add to ID token / access token / userinfo:** ON · **Claim JSON Type:** String

The backend reads this `discord_user_id` claim and persists it on `app_user` (REQ-DATA-006).

> **Required for the approval gate.** The PENDING admin-approval flow (REQ-SEC-017) keys off this
> `discord_user_id` claim to recognise a Discord federated login. If mappers 3a/3b are missing, a
> Discord user still passes the membership gate (step 4) but is created `ACTIVE` and skips admin
> approval — so do not skip 3a/3b.

---

## 4. Bind the fail-closed membership gate (the safety property)

The gate runs in a dedicated *First Broker Login* flow.

1. Realm `iri` → **Authentication → Flows** → duplicate **First broker login** →
   name it `First broker login - discord`.
2. In that copy, **Add step** → **Discord Guild + KRT-Mitglied Gate** → set it **Required**.
   Place it **before** the *Review Profile / Create User* steps so a non-member is denied before any
   user is created.
3. On that execution → **⚙ → Config**:
   - **Guild ID:** the das-kartell guild id (step 0).
   - **KRT-Mitglied role ID:** the numeric role id (step 0).
   - **Discord API base URL:** leave the default `https://discord.com/api/v10`.
4. Realm `iri` → **Identity providers → discord → Advanced settings → First Login Flow** →
   select `First broker login - discord`. Save.

The gate calls `GET /users/@me/guilds/{guildId}/member` with the user's brokered token and admits the
login **only** on HTTP 200 with `roles[]` containing the role id. It **fails closed** on any error or
ambiguity (5xx / timeout / malformed / rate-limited), distinct from a clean 404 (not in guild).

---

## 5. Verify

- A Discord account **in** das-kartell **with** KRT-Mitglied → federation completes, lands the user
  PENDING (admin approval required, T1.3), and writes `discord_user_id`.
- A Discord account **not** in the guild, or **without** KRT-Mitglied → login denied, no session.
- Keycloak/SPI logs contain **no** Discord ids, tokens or profile payloads.
- Username/password login is unchanged.
- The Discord consent screen is shown only on the **first** authorization; subsequent logins skip it
  (the provider sends `prompt=none`, so Discord does not re-prompt once the app is authorized for
  these scopes).

> **Enforced once, at first link.** The guild + KRT-Mitglied check runs in the first-broker-login
> flow — i.e. only when the Discord account is first linked, not on every login. A member later
> kicked from the guild or stripped of KRT-Mitglied keeps access until the Track 2 role-sync lands;
> until then, revoke access by disabling/removing the user in Keycloak.

---

## 6. Secrets & rotation

- The Discord **Client Secret**, **Guild ID** and **role ID** live only as Keycloak component config
  supplied at deploy — never in git. The committed realm reference shows `__SET_AT_DEPLOY__`.
- Rotating the client secret: reset it in the Discord app, update the IdP config, no restart needed.
- Re-staging a new provider JAR (step 2) **does** need a Keycloak restart to rebuild providers.

> **Track 2 (later):** the Discord **bot** (bot token + `Server Members` privileged intent, read-only
> invite) and the automated role/unit sync are configured in a separate runbook when Track 2 ships.

