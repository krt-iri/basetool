> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-29.
> **Owner area:** OPS · **Related ADRs:** [ADR-0049](../adr/0049-config-as-promotable-oci-artifact.md)

# Deployment delivery & promotion

## Context & goal

How code and configuration reach the production host, and the safety invariants that
delivery must never violate. The operational runbook (the *how-to*) lives in
[`docs/deployment.md`](../deployment.md); this spec pins the *binding requirements* (the
*what-must-hold*) behind it so they are testable, referenceable, and cannot be silently
eroded. The implementation is `scripts/deploy.sh` driven by `scripts/iri-deploy.{service,timer}`,
the GitHub Actions workflows `release-images.yml` / `promote.yml`, and the
`basetool-config` artifact built from `docker/config/Dockerfile`. See ADR-0049 for the
decision record behind the config-artifact delivery.

These requirements are the first numbered `REQ-OPS-*` ids; the deployment area previously
existed only as a runbook.

## Requirements

### REQ-OPS-001 — Pull-only delivery

The production host **pulls**; nothing pushes to it. There is no inbound SSH, no webhook, and
no GitHub-issued credential capable of running commands on the box. The host holds only a
**read-only** GHCR pull token. A compromised Actions workflow or stolen `GITHUB_TOKEN` must
not be able to drive code execution on prod — at most it can read images already published.

**Acceptance**

- [ ] The host's only inbound deploy credential is a `Packages: Read` GHCR token; no SSH key,
  deploy key, or git credential is provisioned for the deploy path.
- [ ] `deploy.sh` performs only outbound registry operations (`docker login`, `imagetools
  inspect`, `pull`, `create`/`cp`); it opens no listening socket and accepts no inbound call.

**Enforced by:** `scripts/deploy.sh` · `scripts/iri-deploy.service` (sandbox) · **Runbook:** `docs/deployment.md` → *Why this design*

### REQ-OPS-002 — Deliberate promotion

Neither application images nor host configuration reach production on a `main` merge or a
release build. Production moves **only** when an operator runs `promote.yml`, which re-tags an
existing, already-validated digest to `:stable`. The app images and the `basetool-config`
bundle are promoted **in lock-step**, so the compose file the host applies always matches the
image versions it references.

**Acceptance**

- [ ] `release-images.yml` never writes the `:stable` tag for any image (backend/frontend/
  ingest/config).
- [ ] `promote.yml` is `workflow_dispatch`-only and promotes `backend`, `frontend`, `ingest`
  and `config` together (`fail-fast: true`).

**Enforced by:** `.github/workflows/release-images.yml` · `.github/workflows/promote.yml` · **Runbook:** `docs/deployment.md` → *Promoting to production*

### REQ-OPS-003 — Digest pin + health gate + auto-rollback

`deploy.sh` resolves `:stable` to concrete, immutable digests, applies them via a compose
override with `docker compose up -d --wait --wait-timeout`, and on a health-check failure
restores the previous state and exits non-zero. Rollback covers **both** the app-image digest
pin **and** the host config tree swapped in this deploy.

**Acceptance**

- [ ] A `:stable` tag flip in GHCR mid-deploy cannot partially apply: the deploy applies a
  single resolved digest set or none.
- [ ] When the new images fail to become healthy within `IRI_HEALTH_TIMEOUT`, the previous
  digest pin **and** the previous config tree are restored before the run exits non-zero.

**Enforced by:** `scripts/deploy.sh` (rollback block) · **Runbook:** `docs/deployment.md` → *What happens on the server*

### REQ-OPS-004 — Host configuration delivered as a promotable, digest-pinned artifact

The host configuration — `docker-compose.yml`, the NPM maintenance page
(`docker/maintenance/`) and the Keycloak login theme (`keycloak-theme/`) — is delivered to the
host as the signed `basetool-config` OCI artifact over the same pull-only, digest-pinned,
deliberately-promoted GHCR channel as the app images. The idempotence marker
(`last-deployed.digests`) **includes the config-bundle digest**, so a config-only change (e.g.
a bumped redis or npm image pin) is detected and applied — it is never skipped by the
app-image idempotence check. No manual `cp docker-compose.yml` or hand-run
`docker compose up -d` is required for an auto-appliable change.

**Acceptance**

- [ ] After a promotion whose only change is an infra pin bump, the next timer tick applies
  the new compose and recreates the affected container without operator file copying.
- [ ] `deploy.sh`'s idempotence marker carries four fields (`backend|frontend|ingest|config`);
  a changed config digest alone makes the marker differ and triggers an apply.
- [ ] A missing/unresolvable `basetool-config` artifact degrades to the legacy app-only deploy
  rather than failing the loop.

**Enforced by:** `scripts/deploy.sh` (config-delivery block, `EXPECTED_MARKER`) · `docker/config/Dockerfile` · `.github/workflows/release-images.yml` (`build-config`) · **Runbook:** `docs/deployment.md` → *Infra / host-config bumps*

### REQ-OPS-005 — No secrets in the delivered bundle

The `basetool-config` bundle is an explicit allowlist and must **never** carry host secrets —
`.env`, `keystore.p12` (or any `*.p12`/`*.jks`/`*.pem`/`*.key`), `realm-export.json`, or the
`keycloak/providers/` JARs. This is enforced at three layers: the Dockerfile's COPY allowlist,
`.dockerignore` barring secrets from the build context, a CI assertion that pulls the built
bundle and fails the release on any secret-shaped file, and a final re-assertion in
`deploy.sh` before the bundle is applied.

**Acceptance**

- [ ] `release-images.yml` fails if the built `basetool-config` bundle contains a
  secret-shaped file or is missing `docker-compose.yml`.
- [ ] `deploy.sh` aborts before applying if the staged bundle contains `.env`, a keystore, a
  `realm-export.json`, or a `keycloak/providers` directory.

**Enforced by:** `docker/config/Dockerfile` · `.dockerignore` · `.github/workflows/release-images.yml` (*Assert config bundle carries no host secrets*) · `scripts/deploy.sh` (`assert_no_secrets`)

### REQ-OPS-006 — Stateful-infra changes are operator-gated

A change to the **postgres** or **Keycloak** image pin is a stateful, choreographed upgrade
(PGDATA major migration; Keycloak provider + keystore-SAN dance) that a blind `up -d` would
break and the health gate would then roll back in a loop. `deploy.sh` must detect such a change
and refuse to auto-apply it, alert once, then skip subsequent ticks quietly until a new
promotion or an explicit operator `--force` after the documented manual upgrade. redis and npm
image bumps are auto-applied.

**Acceptance**

- [ ] A promotion whose compose changes a `postgres:` or `quay.io/keycloak/keycloak:` pin is
  not auto-applied; the run records the block and exits non-zero on first encounter, then
  skips quietly on repeat ticks.
- [ ] `deploy.sh --force` applies a previously-gated stateful-infra change.
- [ ] A redis or npm image bump (no postgres/Keycloak change) is auto-applied.

**Enforced by:** `scripts/deploy.sh` (`infra_image_pins`, carve-out block) · **Runbook:** `docs/deployment.md` → *Stateful-infra upgrades*

## Out of scope

- The deploy script (`deploy.sh`) and the systemd units themselves are **not** delivered via
  the bundle (self-update hazard) — they remain a manual bootstrap step. Bootstrap, token
  rotation, and the maintenance-page mechanics live in `docs/deployment.md`.
- Application-level configuration delivered as environment variables in `.env` is host-only and
  out of scope here (it is never bundled). The list of env keys lives in `README.md`.

## Open questions

- Deepening the infra health gate beyond `redis-cli ping` / `pg_isready` (which do not
  exercise the app workload) — e.g. recreating dependents on a config-digest change so their
  healthchecks gate the rollback. Promote to an ADR if pursued.

