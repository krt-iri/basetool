# IRIDIUM Basetool — Deployment Runbook

## Overview

Production deployment runs as a closed loop between three actors:

```
   ┌──────────────────────────────┐       ┌────────────────────────────┐
   │  GitHub Actions              │       │  GitHub Container Registry │
   │                              │       │                            │
   │  .github/workflows/          │  push │  ghcr.io/krt-iri/          │
   │    release-images.yml ───────┼──────►│    basetool-backend:1.4.2  │
   │      build  + push           │       │    basetool-frontend:1.4.2 │
   │      scan   (Trivy SARIF)    │       │      ... :latest, :edge,   │
   │      sign   (cosign keyless) │       │      :sha-abc1234, :stable │
   │                              │       │                            │
   │  .github/workflows/          │       │                            │
   │    promote.yml      ─────────┼──────►│    (re-tags an existing    │
   │      manual dispatch         │       │     digest to :stable)     │
   └──────────────────────────────┘       └────────────────────┬───────┘
                                                               │
                                                               │ docker pull
                                                               │
                                          ┌────────────────────▼───────┐
                                          │  Production host           │
                                          │                            │
                                          │  /var/iri/code/            │
                                          │    docker-compose.yml      │
                                          │    .env                    │
                                          │    scripts/deploy.sh ──┐   │
                                          │                        │   │
                                          │  /var/iri/secrets/     │   │
                                          │    keystore.p12        │   │
                                          │                        │   │
                                          │  /etc/iri/             │   │
                                          │    ghcr-pull-token     │   │
                                          │                        │   │
                                          │  /var/lib/iri/         │   │
                                          │    current-digest-pin.yml  │
                                          │    previous-digest-pin.yml │
                                          │    last-deployed.digests   │
                                          │                            │
                                          │  systemd: iri-deploy.timer │
                                          │    OnUnitActiveSec=5min    │
                                          └────────────────────────────┘
```

**What gets pushed to GHCR carries no secrets.** The keystore, `.env`,
`realm-export.json`, and the keycloak theme directory all live on the host
filesystem and are bind-mounted into the containers at runtime. The
`.dockerignore` at the repo root is a belt-and-suspenders guard against ever
including them in a build context.

**The host pulls; nothing pushes to the host from GitHub.** The deploy timer
holds a read-only GHCR token. There is no inbound SSH, no webhook, no
GitHub-issued credential capable of running shell commands on the box.

**Tag promotion is deliberate.** `release-images.yml` publishes versioned
tags (`:1.4.2`, `:latest`, `:edge`, `:sha-abc1234`) on every main push and
git tag. None of those flips the `:stable` pointer that the server polls.
That happens only when an operator runs the `promote.yml` workflow with an
explicit version.

---

## Initial server bootstrap (one-time)

The current "copy the whole `basetool` folder to the server" workflow becomes
a small, deliberate set of files that live on the host. After bootstrap, no
further file sync between developer machines and the server is needed —
updates arrive only as GHCR image pulls.

### 1. System packages

```bash
sudo apt update
sudo apt install --no-install-recommends \
    docker.io docker-compose-v2 \
    logrotate \
    curl ca-certificates
```

Docker Engine ≥ 23.x is required for `docker compose up --wait`.

### 2. Dedicated `deploy` user

A non-root user with no shell and group membership only in `docker`:

```bash
sudo useradd --system --no-create-home --shell /sbin/nologin --groups docker deploy
```

The systemd unit runs as this user. It has no sudo, no SSH access, and
cannot escape the docker-group blast radius.

### 3. Directory layout

```bash
sudo mkdir -p /var/iri/code            # compose file, scripts
sudo mkdir -p /var/iri/secrets         # keystore.p12 lives here
sudo mkdir -p /var/iri/backend/log     # backend log dir (uid 10001)
sudo mkdir -p /var/iri/frontend/log    # frontend log dir (uid 10001)
sudo mkdir -p /var/iri/db-backend      # postgres data
sudo mkdir -p /var/iri/db-keycloak     # keycloak postgres data
sudo mkdir -p /var/iri/keycloak/log    # keycloak file log
sudo mkdir -p /var/iri/redis           # redis AOF
sudo mkdir -p /var/iri/npm/data        # nginx-proxy-manager state
sudo mkdir -p /var/iri/npm/letsencrypt
sudo mkdir -p /var/lib/iri             # deploy state
sudo mkdir -p /etc/iri                 # token

# Log dirs need to be writable by the in-container uid 10001 (set in the
# backend / frontend Dockerfiles).
sudo chown -R 10001:10001 /var/iri/backend/log /var/iri/frontend/log
sudo chown -R deploy:docker /var/lib/iri /var/iri/code
```

### 4. Compose file + scripts

Copy from the repository — only these two trees, never the rest:

```bash
sudo cp docker-compose.yml      /var/iri/code/
sudo cp -r scripts/             /var/iri/code/
sudo cp -r keycloak-theme/      /var/iri/code/
sudo chown -R deploy:docker     /var/iri/code
sudo chmod 0755                 /var/iri/code/scripts/deploy.sh
```

`docker-compose.build.yml` does **not** belong on the production host. It
has no purpose there and removing it eliminates any risk of an accidental
`docker compose ... --build` that would attempt to rebuild from a
non-existent source tree.

### 5. Production secrets

#### 5.1 `.env`

```bash
sudo cp .env.example /var/iri/code/.env
sudo chmod 0640 /var/iri/code/.env
sudo chown deploy:docker /var/iri/code/.env
sudo nano /var/iri/code/.env
```

Fill in every `CHANGE_ME`. `IRI_KEYSTORE_HOST_PATH` should point at
`/var/iri/secrets/keystore.p12`. Leave `IRI_BASETOOL_VERSION` unset (the
default `stable` is what the deploy script wants).

#### 5.2 PKCS12 keystore

Place the production `keystore.p12` at the canonical path. It is
read-only-mounted into both containers as `/run/secrets/keystore.p12`:

```bash
sudo install -m 0640 -o root -g 10001 /path/to/keystore.p12 /var/iri/secrets/keystore.p12
```

`gid=10001` matches the in-container app user, so the JVM can read the
file. Owner root prevents the deploy user from rewriting it; rotation is a
deliberate sudo action.

#### 5.3 Keycloak realm export

```bash
sudo install -m 0640 -o deploy -g docker /path/to/realm-export.json /var/iri/code/realm-export.json
```

#### 5.4 GHCR pull token

Generate a fine-grained PAT in GitHub:
- Repository access: `krt-iri/basetool` only
- Permissions: `Packages: Read` (no other scopes)
- Expiry: 90 days (calendar a rotation reminder)

```bash
sudo install -m 0600 -o deploy -g deploy /dev/stdin /etc/iri/ghcr-pull-token <<< 'ghp_xxxxxxxx'
```

The token file is owner-only readable. The deploy user uses `cat` against
it (via the systemd unit), nothing else touches it.

### 6. Install the systemd timer

```bash
sudo cp /var/iri/code/scripts/iri-deploy.service /etc/systemd/system/
sudo cp /var/iri/code/scripts/iri-deploy.timer   /etc/systemd/system/
sudo cp /var/iri/code/scripts/iri-deploy.logrotate /etc/logrotate.d/iri-deploy

sudo touch /var/log/iri-deploy.log
sudo chown deploy:adm /var/log/iri-deploy.log
sudo chmod 0640        /var/log/iri-deploy.log

sudo systemctl daemon-reload
sudo systemctl enable --now iri-deploy.timer
```

Verify:

```bash
systemctl status iri-deploy.timer
systemctl list-timers iri-deploy.timer
```

### 7. First deploy

The timer's first firing is `OnBootSec=5min` after install. To not wait:

```bash
sudo systemctl start iri-deploy.service
journalctl -u iri-deploy.service -f
```

This pulls `:stable`, applies, waits for health, then exits. The stack is
live.

---

## Normal deploy flow

### Cutting a release

From a developer's laptop, working on the `main` branch:

```bash
git tag -a v1.4.3 -m "Release v1.4.3"
git push origin v1.4.3
```

The push to `v*.*.*` fires `release-images.yml`. Within ~10 minutes the
images are built, scanned, signed, and available in GHCR as:

```
ghcr.io/krt-iri/basetool-backend:1.4.3   (also :1.4, :1, :latest)
ghcr.io/krt-iri/basetool-frontend:1.4.3
```

At this point **nothing is deployed yet**. `:stable` still points at the
previously promoted version. Production is unaffected.

### Promoting to production

```bash
gh workflow run promote.yml -f version=1.4.3
```

(or use the GitHub Actions UI: *Actions → Promote to stable → Run
workflow → version `1.4.3`*)

This re-tags the existing 1.4.3 image digest as `:stable` in GHCR. No
rebuild. Same digest, two tags.

### What happens on the server

Within at most 5 minutes (timer interval + RandomizedDelaySec):
1. `iri-deploy.service` fires.
2. `deploy.sh` resolves `:stable` → digest, compares with
   `/var/lib/iri/last-deployed.digests`.
3. If different: writes `current-digest-pin.yml` with the new digest pair,
   runs `docker compose pull && docker compose up -d --wait`.
4. On all-healthy: updates `last-deployed.digests`, logs success.
5. On any-unhealthy within 180 s: restores `previous-digest-pin.yml`,
   re-ups, logs failure, exits non-zero (journald and OnFailure= hooks
   pick it up).

You see the result in `journalctl -u iri-deploy.service -n 100` or in
`/var/log/iri-deploy.log`.

### Forcing an immediate run

```bash
sudo systemctl start iri-deploy.service
```

This is also the runbook step you take after a manual `:stable` promotion
if you do not want to wait for the next tick.

---

## Manual deploy / rollback

### Pin to a specific version (forward or backward)

```bash
sudo -u deploy /var/iri/code/scripts/deploy.sh --tag 1.4.2
```

Any tag the registry resolves works: `latest`, `edge`, `1.4.2`, `1.4`,
`sha-abc1234`. The script then continues to poll `:stable` on the next
timer tick — so a manual `--tag 1.4.2` is **not** persistent. If you want
a sticky rollback, also flip `:stable` itself:

```bash
gh workflow run promote.yml -f version=1.4.2
```

Now subsequent timer ticks pick up `1.4.2` as `:stable` and the rollback is
durable.

### Dry-run check

```bash
sudo -u deploy /var/iri/code/scripts/deploy.sh --check-only
```

Resolves the digest the next deploy would target and prints it. No
restarts.

### Force a fresh pull regardless of digest match

```bash
sudo rm /var/lib/iri/last-deployed.digests
sudo systemctl start iri-deploy.service
```

Useful after restoring `/var/lib/iri/` from a backup or for re-applying
after a host migration.

---

## Token rotation

GHCR pull tokens are scoped to the basetool repo with `Packages: Read`
only. Rotate every 90 days, or immediately on any suspicion of leak:

```bash
# 1. Generate a new fine-grained PAT in GitHub (same scopes as before).

# 2. Replace the file atomically.
sudo install -m 0600 -o deploy -g deploy /dev/stdin /etc/iri/ghcr-pull-token.new <<< 'ghp_new_token'
sudo mv /etc/iri/ghcr-pull-token.new /etc/iri/ghcr-pull-token

# 3. Force a deploy run to verify the new token works.
sudo systemctl start iri-deploy.service
journalctl -u iri-deploy.service -n 50

# 4. Revoke the old token in GitHub's PAT page only AFTER step 3 succeeded.
```

---

## Troubleshooting

| Symptom | Where to look | Common cause |
|---|---|---|
| Timer fires but image never updates | `journalctl -u iri-deploy.service` | `:stable` not yet promoted. Run `gh workflow run promote.yml -f version=...`. |
| `docker login` fails | `/var/log/iri-deploy.log` | Expired or revoked PAT. See *Token rotation*. |
| Health check times out | `docker compose ps`, `docker logs <service>` | New version broken; the script auto-rolls back. Inspect the rolled-back container's logs for the root cause. |
| Service stays "unhealthy" after rollback | `docker logs db-backend` etc. | Infrastructure-side problem (disk full, DB corruption). Not caused by the deploy. |
| Keystore mount fails on `up` | `docker compose config`, `ls -la /var/iri/secrets` | Keystore file missing or wrong owner. `gid=10001` must be readable. |
| `IRI_KEYSTORE_HOST_PATH` referenced but file not present | `.env` | Sync `.env` and `/var/iri/secrets/keystore.p12` between path and contents. |
| Compose pulls but does not restart | journald log | All target digests match the last-deployed digests — that is the idempotent no-op path. Force-clear `/var/lib/iri/last-deployed.digests` if you want a forced restart. |

---

## Why this design

A few decisions worth keeping in mind when you touch any of the pieces:

- **Pull, not push.** The server never accepts inbound connections from
  GitHub. A compromised Actions workflow or stolen `GITHUB_TOKEN` cannot
  drive code execution on the production host. The PAT on the server can
  only *read* images that were already published.
- **Digest pin between resolution and apply.** `deploy.sh` resolves
  `:stable` to a concrete digest, writes that digest into a compose
  override, and applies *that*. A `:stable` flip in GHCR mid-deploy cannot
  partially apply a half-promoted release; it would only be picked up by
  the next timer tick.
- **Health gate + auto-rollback.** `docker compose up --wait
  --wait-timeout 180` exits non-zero if any service is not healthy in
  three minutes. The script holds the previous digest pin and restores it
  before exiting non-zero, so a bad release self-heals to the last
  known-good revision within roughly five minutes.
- **No image holds the keystore.** This is checked twice — by
  `.gitignore` (CI's checkout never has the file) and by `.dockerignore`
  (no local `docker build` accidentally bakes it in). The production
  keystore lives only on the server, behind a root-owned 0640 file
  whose group matches the in-container app uid.
