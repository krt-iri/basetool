> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-20.
> **Owner area:** AUTH/SEC · **Related ADRs:** ADR-0029 (federation + first-login gate); ADR-0030 (role/unit sync, planned — Track 2)

# Discord integration — login, membership gate & admin approval

## Context & goal

Members should be able to log in to Basetool with their Discord account, but **only** if they are
verified DAS KARTELL members — present in the `das-kartell` Discord guild **and** holding the
`KRT-Mitglied` role. Discord login is **additive** to the existing Keycloak credential login. A
brand-new Discord user lands in a **PENDING** state with no access until an admin approves; admins
are notified. After approval, Basetool roles and org-unit memberships are assigned **manually** with
the existing tooling — the automated Discord-role → app-role/unit **sync is a separate, later track**
(Track 2) and is out of scope here.

This is epic **#720, Track 1** (issues #721–#725). The federation and the gate are implemented in an
owned Keycloak provider module (`keycloak-spi/`), per [ADR-0029](../adr/0029-discord-federation-first-login-membership-gate.md).

## Requirements

### REQ-DATA-006 — Discord account link on the user

Every Basetool user MAY carry a single Discord account id. The `app_user.discord_user_id` column
(nullable, **unique**, text) records the Discord user id (numeric snowflake). It is written by the
Keycloak Discord identity-provider attribute mapper into the token and persisted by the backend at
login (auto-link). `null` for users who never used Discord. Because the link is the recognition key
for a returning Discord user, it must be unique across users; Postgres treats `NULL` as distinct, so
all credential-only users coexist.

**Acceptance**

- [ ] `app_user.discord_user_id` exists: nullable, `VARCHAR(32)`, with a unique constraint (V171).
- [ ] The JPA `User` entity maps it (`@Column(name = "discord_user_id", unique = true)`), and
  `ddl-auto: validate` boots clean against the migration.
- [ ] Two distinct users cannot hold the same non-null Discord id (DB unique).
- [](T1.3) On login of a federated Discord identity, the backend persists the `discord_user_id`
  claim onto the user row; a credential login leaves it untouched.

**Enforced by:** `BackendApplicationTests` (schema validate) · _(planned T1.3: link-persistence test)_ · **Code:** `User`, `V171__add_discord_user_id_to_app_user.sql`, _(T1.3) `UserService.syncUser`_ · **Issues:** #721, #724

### REQ-SEC-016 — Fail-closed guild + KRT-Mitglied membership gate

A Discord login MUST be denied (no Keycloak session issued) unless the federated Discord user is a
member of the configured guild **and** holds the configured `KRT-Mitglied` role, matched by
**numeric role id** (never display name). The check runs inside the Keycloak first-broker-login flow
(`DiscordGuildRoleGateAuthenticator`), calling `GET /users/@me/guilds/{guildId}/member` with the
brokered user access token (scope `guilds.members.read`). It **fails closed**: any ambiguity — 5xx,
timeout, network error, malformed body, or `429` after the retry budget — denies the login, distinct
from a clean `404` (not in guild). Tokens, payloads and Discord ids are **never logged**.

**Acceptance**

- [x] In-guild **and** holds `KRT-Mitglied` (HTTP 200, `roles[]` ∋ role id) ⇒ login allowed.
- [x] In-guild but missing the role ⇒ denied.
- [x] Not in guild (clean 404) ⇒ denied.
- [x] 5xx / timeout / malformed body / 429-after-retries ⇒ denied (**fail closed**).
- [x] Role is matched by numeric id; renaming the Discord role does not change the outcome.
- [ ] No token, payload or Discord id appears in any log line. _(by design — only the coarse decision is logged; proven by the T1.4 PII grep.)_
- [ ] Credential (non-Discord) login is unaffected by the gate. _(T1.4 e2e.)_

**Enforced by:** `DiscordMembershipCheckerTest` (keycloak-spi) proves the decision matrix · _(planned T1.4: login-gate e2e + log PII grep)_ · **Code:** `DiscordGuildRoleGateAuthenticator(+Factory)`, `DiscordMembershipChecker` · **Issues:** #723, #725

### REQ-SEC-017 — PENDING approval withholds all authorities _(planned — T1.3)_

A brand-new Discord login lands `PENDING` and is granted **no** authorities until an admin approves:
the entire authority assembly (realm roles + permissions + org-unit membership + cascade) is
short-circuited to a single `ROLE_PENDING_APPROVAL`, and `ROLE_GUEST` is **not** carried. Approval
moves the user to `ACTIVE`; rejection keeps them denied. Keycloak `ADMIN`-realm-role holders are
auto-`ACTIVE` (bootstrap safety). After approval, roles/units are assigned manually (Track 1) — no
automated mapping.

**Acceptance** _(filled in T1.3)_

- [ ] PENDING ⇒ only `ROLE_PENDING_APPROVAL`, even if the JWT carries realm roles; no GUEST-readable
  endpoint leaks org data.
- [ ] First admin (Keycloak `ADMIN` realm role) is `ACTIVE` on first login.
- [ ] Approve ⇒ `ACTIVE` + audit row; reject ⇒ denied + reason; legacy rows backfilled `ACTIVE`.
- [ ] Concurrent approve ⇒ 409 (optimistic `@Version`).

**Enforced by:** _(planned T1.3 tests)_ · **Code:** _(T1.3) `CustomJwtGrantedAuthoritiesConverter`, `UserService`, registrations controller_ · **Issues:** #724

### REQ-NOTIF-012 — Admins notified on new PENDING registration _(planned — T1.3)_

When a new Discord user enters `PENDING`, every admin receives one in-app notification (no Discord id
or PII in the payload), via the existing data-driven notification rule engine (a `ROLE` selector with
`roleCode = 'ADMIN'`, mirroring V160/V161).

**Acceptance** _(filled in T1.3)_

- [ ] One notification per admin on a new PENDING registration; the actor is not self-notified.
- [ ] No Discord id / token / e-mail in the notification or logs.

**Enforced by:** _(planned T1.3 tests)_ · **Code:** _(T1.3) `DiscordRegistrationPendingEvent`, `V173` seed_ · **Issues:** #724

## Out of scope

- **Automated Discord-role → app-role/org-unit sync** and the Discord **bot** — Track 2 (#726–#730),
  ADR-0030 (planned). Track 1 keeps Basetool roles manual.
- **Discord OAuth application + Keycloak realm provisioning** (client id/secret, IdP, mappers, the
  custom first-broker-login flow, the gate config) — operator steps in
  [`DISCORD_KEYCLOAK_SETUP.md`](../keycloak/DISCORD_KEYCLOAK_SETUP.md); never committed secrets.

## Open questions

- **Baseline floor on approval** — does approval auto-grant a baseline `SQUADRON_MEMBER`, or does the
  admin seat every role by hand? Track-1 default: by hand (epic open decision #1).
- **Existing-member migration** — link an existing credential account to a Discord identity, or only
  forward via Discord login? (epic open decision #2). Promote the resolved answer to an ADR.

