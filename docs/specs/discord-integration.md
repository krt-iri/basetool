> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-20.
> **Owner area:** AUTH/SEC · **Related ADRs:** ADR-0030 (federation + first-login gate); ADR-0031 (role/unit sync, planned — Track 2)

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
owned Keycloak provider module (`keycloak-spi/`), per [ADR-0030](../adr/0030-discord-federation-first-login-membership-gate.md).

## Requirements

### REQ-DATA-006 — Discord account link on the user

Every Basetool user MAY carry a single Discord account id. The `app_user.discord_user_id` column
(nullable, **unique**, text) records the Discord user id (numeric snowflake). It is written by the
Keycloak Discord identity-provider attribute mapper into the token and persisted by the backend at
login (auto-link). `null` for users who never used Discord. Because the link is the recognition key
for a returning Discord user, it must be unique across users; Postgres treats `NULL` as distinct, so
all credential-only users coexist.

**Acceptance**

- [ ] `app_user.discord_user_id` exists: nullable, `VARCHAR(32)`, with a unique constraint (V172).
- [ ] The JPA `User` entity maps it (`@Column(name = "discord_user_id", unique = true)`), and
  `ddl-auto: validate` boots clean against the migration.
- [ ] Two distinct users cannot hold the same non-null Discord id (DB unique).
- [](T1.3) On login of a federated Discord identity, the backend persists the `discord_user_id`
  claim onto the user row; a credential login leaves it untouched.
- [x] A Discord login is recognised **only** by the Keycloak subject / `discord_user_id`, never by
  `preferred_username`. The legacy username fallback (kept for pre-UUID credential rows) is suppressed
  for a Discord login, so a fresh Discord identity is never silently matched onto a pre-existing row —
  no account-link or privilege inheritance, and the PENDING gate (REQ-SEC-017) can never be bypassed
  that way. Track 1 does no auto-linking of an existing account to a Discord identity (open decision #2).

**Enforced by:** `BackendApplicationTests` (schema validate) · `UserServiceDiscordSyncTest` (subject-only recognition: a Discord login never consults `findByUsername`) · _(planned T1.3: link-persistence test)_ · **Code:** `User`, `V172__add_discord_user_id_to_app_user.sql`, `UserService.syncUser` · **Issues:** #721, #724

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
- [x] The anonymous sidebar exposes a **localized** Discord login entry point (`nav.login.discord`, all three message bundles) that brokers the login this gate guards. It carries the Discord brand mark, which inherits the link colour (`currentColor`) like the footer GitHub mark — no hard-coded blurple, per the monochrome-icon design-system convention.
- [x] The Keycloak login page itself renders configured (non-hidden) IdPs as social buttons via the
  krt-theme `login.ftl` social block, so the Discord entry point is reachable from the credential
  form, the extractor's device-grant verification page, and any direct login — not only the app
  sidebar. Requires the `discord` IdP's "Hide on login page" = OFF.

**Enforced by:** `DiscordMembershipCheckerTest` (keycloak-spi) proves the decision matrix · `MessageBundleConsistencyTest` (frontend) pins the `nav.login.discord` key across the default/de/en bundles · _(planned T1.4: login-gate e2e + log PII grep)_ · **Code:** `DiscordGuildRoleGateAuthenticator(+Factory)`, `DiscordMembershipChecker`, `fragments/sidebar.html`, `fragments/icons.html` (`krt-icon-discord`) · **Issues:** #723, #725

### REQ-SEC-017 — PENDING approval withholds all authorities (fail-safe default)

**Every** brand-new **non-admin** registration lands `PENDING` and is granted **no** authorities
until an admin approves — independent of whether the login arrived via Discord or credentials. The
PENDING decision is deliberately **decoupled from Discord detection**: it must not depend on the
optional `discord_user_id` claim/mapper, otherwise a misconfigured Keycloak (attribute/protocol
mapper absent) would let a federated login inherit the `ACTIVE` default and silently skip approval.
For a PENDING (or `REJECTED`) account the entire authority assembly (realm roles + permissions +
org-unit membership + cascade) is short-circuited to a single `ROLE_PENDING_APPROVAL`, and
`ROLE_GUEST` is **not** carried. Approval moves the user to `ACTIVE`; rejection keeps them denied.
Keycloak `ADMIN`-realm-role holders are auto-`ACTIVE` (bootstrap safety — the first admin can never be
locked out). Both creation paths apply the rule — the interactive login (`syncUser(Jwt)`) and the
scheduled Keycloak reconciliation (`syncUser(KeycloakUserDto)`) — so the scheduler can never
pre-create an `ACTIVE` row that a later login would inherit. Admins are **notified** only for genuine
Discord self-registrations (REQ-NOTIF-012); a credential account is created by an admin in Keycloak
who already sees it in the pending queue, so it raises no extra notification. After approval,
roles/units are assigned manually (Track 1) — no automated mapping.

> **Trade-off (owner-approved 2026-06-20).** Making the default fail-safe means a brand-new
> **credential** account (created directly in Keycloak) now also requires a one-time Basetool
> approval, and the scheduled sync materialises not-yet-seen Keycloak users as `PENDING` rather than
> `ACTIVE`. This is the accepted cost of closing the mapper-misconfiguration bypass; pre-existing rows
> stay `ACTIVE` (V173 backfill), so only accounts created after this change are affected.
>
> The behaviour is gated by `app.registration.require-approval` (default **`true`** = prod). It is set
> to `false` **only** in the Playwright e2e stack (`APP_REGISTRATION_REQUIRE_APPROVAL=false` in
> `docker-compose.e2e.yml`), where `BackendSeeder` provisions fixture users on the fly and an
> interactive approval step would deadlock seeding on an ephemeral DB (no V173 backfill). The approval
> lifecycle itself stays covered by backend unit tests, not e2e.

**Acceptance**

- [x] PENDING/REJECTED ⇒ only `ROLE_PENDING_APPROVAL`, even if the JWT carries realm roles; membership
  is never consulted and `ROLE_GUEST` is not carried.
- [x] Every brand-new non-admin registration ⇒ `PENDING`, whether via Discord **or** credentials, and
  regardless of whether the `discord_user_id` claim is present (mapper-independent fail-safe).
- [x] The scheduled sync (`syncUser(KeycloakUserDto)`) creates a brand-new non-admin user `PENDING`
  too; it never changes an existing user's approval state.
- [ ] First admin (Keycloak `ADMIN` realm role) is `ACTIVE` on first login. _(syncUser carve-out; T1.4 e2e.)_
- [x] Approve ⇒ `ACTIVE` + audit row; reject ⇒ `REJECTED` + reason in the audit.
- [x] Concurrent approve ⇒ 409 (optimistic `@Version`).
- [ ] Legacy rows backfilled `ACTIVE` (V173). _(schema-validated on boot; T1.4 e2e.)_

**Enforced by:** `CustomJwtGrantedAuthoritiesConverterTest` (gate) + `UserServiceApprovalTest` (approve/reject + 409) + `UserServiceDiscordSyncTest` (new credential ⇒ PENDING, new admin ⇒ ACTIVE) + `UserServiceSyncTest` (scheduled-sync fail-safe) · **Code:** `CustomJwtGrantedAuthoritiesConverter`, `UserService`, `DiscordRegistrationAdminController`, `BackendRoleSyncFilter` (waiting-page route) · **Issues:** #724

### REQ-NOTIF-012 — Admins notified on new PENDING registration

When a new Discord user enters `PENDING`, every admin receives one in-app notification (no Discord id
or PII in the payload), via the existing data-driven notification rule engine (a `ROLE` selector with
`roleCode = 'ADMIN'`, mirroring V160/V161).

**Acceptance**

- [x] A new PENDING registration publishes a `DISCORD_REGISTRATION_PENDING` after-commit event whose
  default rule (V174) resolves to every admin via a `ROLE` selector.
- [x] No Discord id / token / e-mail rides the event (it carries only the user id + username).
- [ ] Exactly one notification per admin, end to end. _(notification engine; T1.4 e2e.)_

**Enforced by:** `DiscordRegistrationPendingEvent` (no PII by construction) + `V174` seed; the rule-engine fan-out is covered by the epic-#622 tests · **Code:** `UserService.syncUser`, `DiscordRegistrationPendingEvent`, `V174` · **Issues:** #724

## Out of scope

- **Automated Discord-role → app-role/org-unit sync** and the Discord **bot** — Track 2 (#726–#730),
  ADR-0031 (planned). Track 1 keeps Basetool roles manual.
- **Continuous membership enforcement.** The guild + KRT-Mitglied gate (REQ-SEC-016) runs **once**,
  at first-broker-login when the Discord identity is first linked. A member later removed from the
  guild or stripped of `KRT-Mitglied` keeps Basetool access until the Track 2 role-sync revokes it —
  Track 1 does no periodic re-check.
- **Discord OAuth application + Keycloak realm provisioning** (client id/secret, IdP, mappers, the
  custom first-broker-login flow, the gate config) — operator steps in
  [`DISCORD_KEYCLOAK_SETUP.md`](../keycloak/DISCORD_KEYCLOAK_SETUP.md); never committed secrets.

## Open questions

- **Baseline floor on approval** — does approval auto-grant a baseline `SQUADRON_MEMBER`, or does the
  admin seat every role by hand? Track-1 default: by hand (epic open decision #1).
- **Existing-member migration** — link an existing credential account to a Discord identity, or only
  forward via Discord login? (epic open decision #2). Promote the resolved answer to an ADR.

