# Profit Basetool — Deployment Runbook

## Overview

Production deployment runs as a closed loop between three actors:

```
┌──────────────────────────────┐       ┌────────────────────────────┐
│  GitHub Actions              │       │  GitHub Container Registry │
│                              │       │                            │
│  .github/workflows/          │  push │  ghcr.io/krt-profit/          │
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

# Docker config dir for the deploy user. `docker login` writes its
# credentials.json into $DOCKER_CONFIG (default $HOME/.docker), and the
# deploy user has no $HOME because it was created with --no-create-home in
# step 2. deploy.sh sets DOCKER_CONFIG=/var/lib/iri/.docker explicitly; we
# pre-create the dir here with 0700 so the credentials file is exclusive
# to the deploy user.
sudo install -d -m 0700 -o deploy -g docker /var/lib/iri/.docker
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
read-only-mounted as `/run/secrets/keystore.p12` into every service that
serves or trusts the internal cert — backend, frontend, ingest **and**
Keycloak:

```bash
sudo install -m 0644 -o root -g 10001 /path/to/keystore.p12 /var/iri/secrets/keystore.p12
```

It must be **world-readable (`0644`)**: the backend/frontend/ingest images
run as uid `10001`, but the Keycloak (Quarkus) image runs as uid `1000`, so
no single owner/group covers both — a stricter `0640` makes Keycloak fail to
start with `AccessDeniedException /run/secrets/keystore.p12`. Owner root keeps
the deploy user from rewriting it; rotation is a deliberate sudo action.
(Acceptable exposure: a self-signed internal cert on a single-purpose host;
the public Let's Encrypt cert lives in NPM, not here.)

#### 5.3 Keycloak realm export

```bash
sudo install -m 0640 -o deploy -g docker /path/to/realm-export.json /var/iri/code/realm-export.json
```

#### 5.4 GHCR pull token

Generate a fine-grained PAT in GitHub:
- Repository access: `krt-profit/basetool` only
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

Releases are cut through a two-phase, PR-based GitHub Actions flow — there is
**no** hand-pushed git tag and no tag is ever force-moved. Tidying the CHANGELOG
and regenerating the CycloneDX SBOMs happens automatically as part of the run.
The SBOMs are a **release-only** artefact: an ordinary `./gradlew build` (local
or CI) never regenerates them, so the committed `*/docs/*-bom.{json,xml}` only
ever change through this flow (or a deliberate `./gradlew :<module>:cyclonedxBom`).

**Phase 1 — prepare.** Trigger *Actions → Release · Prepare → Run workflow* and
enter the version **without** the leading `v` (e.g. `1.4.3`). Off `main` the
workflow:
- reconciles any historical `[Unreleased]` CHANGELOG drift and **cuts**
`[Unreleased]` into a dated `## [v1.4.3]` section;
- regenerates `*/docs/*-bom.{json,xml}` (pure serial-number / timestamp churn is
discarded — only a real dependency change is kept);
- commits the result on a `release/v1.4.3` branch and opens a
`chore(release): v1.4.3` PR carrying a preview of the release notes.

**Phase 2 — merge.** Review and **merge** that PR. The merge *is* the release:
`release-publish.yml` then
- creates the `v1.4.3` tag **once**, at the merge commit (which already carries
the refreshed CHANGELOG + SBOM, so the tag is never moved afterwards);
- publishes the GitHub Release (notes + image links + the four SBOM files as
assets);
- and the tag push fires `release-images.yml`.

Closing the prep PR without merging cancels the release cleanly — no tag, no
orphan commit on `main`.

> **`RELEASE_TOKEN` is required for the images to build automatically.** A tag
> created by CI with the default `GITHUB_TOKEN` does not trigger other workflows
> (GitHub's anti-recursion rule), so `release-publish.yml` creates the tag with
> the `RELEASE_TOKEN` secret (a fine-grained PAT or GitHub App with `contents:
> write`) so that `release-images.yml` fires. If the secret is absent the tag and
> the GitHub Release are still produced, but you must start the image build by
> hand (*Actions → Release Images → Run workflow → `v1.4.3`*); the publish job
> logs a warning saying exactly this. `RELEASE_TOKEN` is a CI secret, separate
> from the server's GHCR-pull PAT under [Token rotation](#token-rotation).

Within ~10 minutes the images are built, scanned, signed, and available in GHCR
as:

```
ghcr.io/krt-profit/basetool-backend:1.4.3   (also :1.4, :1, :latest)
ghcr.io/krt-profit/basetool-frontend:1.4.3
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

## Maintenance page

While `deploy.sh` cycles `backend` and `frontend` out and the new images are
booting, the upstream behind `nginx-proxy-manager` (NPM) momentarily returns
`502 Bad Gateway`. NPM intercepts those failures and serves a branded
maintenance page in their place, so a user hitting the site mid-deploy sees a
deliberate "we'll be right back" screen instead of nginx's default error page.

### How it works

NPM's per-server hook (`/data/nginx/custom/server_proxy.conf`, included in
every proxy host's `server { }` block) wires up:

```nginx
error_page 502 503 504 =503 @maintenance;

location @maintenance {
    internal;
    root /usr/share/nginx/html;
    rewrite ^.*$ $maintenance_target break;

    types {
        application/problem+json json;
        text/html                html;
    }

    add_header Retry-After   "60"       always;
    add_header Cache-Control "no-store" always;
}
```

`$maintenance_target` is set by a small `map` in `/data/nginx/custom/http.conf`
that branches on the request's `Accept` header:

|                    `Accept`                    |      Response      |               Content-Type                |
|------------------------------------------------|--------------------|-------------------------------------------|
| `text/html`, `*/*`, missing                    | `maintenance.html` | `text/html; charset=utf-8`                |
| `application/json`, `application/problem+json` | `maintenance.json` | `application/problem+json; charset=utf-8` |

Both responses are returned with HTTP `503 Service Unavailable` and
`Retry-After: 60`. The HTML page auto-refreshes every 30 seconds, so a user
who lands on it during a deploy is back on the real app as soon as the new
containers pass their healthcheck. AJAX/fetch calls from the live frontend
receive an RFC 7807 `application/problem+json` document and can render their
existing "backend unreachable" toast cleanly.

### Where the files live

All assets are part of the repo and mounted into the NPM container via
`docker-compose.yml`. The static assets directory is mounted read-only;
the two nginx snippets are mounted read-write because NPM's startup
script `s6-rc.d/prepare/50-ipv6.sh` runs `sed -i` against every `*.conf`
under `/data/nginx/` to add IPv6 listeners — with `:ro` the write fails
with `EROFS` and the container refuses to boot. Our snippets contain no
`listen` directives, so `sed` produces byte-identical content; the file
is touched but unchanged.

```
docker/maintenance/
├── nginx/
│   ├── http.conf            -> /data/nginx/custom/http.conf
│   └── server_proxy.conf    -> /data/nginx/custom/server_proxy.conf
└── static/
    ├── maintenance.html     -> /usr/share/nginx/html/maintenance/maintenance.html
    └── maintenance.json     -> /usr/share/nginx/html/maintenance/maintenance.json
```

Nothing in `scripts/deploy.sh` touches these files. They are static, the
trigger is purely an upstream `5xx`, so the page appears for the exact window
between "old container gone" and "new container healthy" — the same window
`docker compose up -d --wait` is already gating on.

### Verifying after a config change

After editing any file under `docker/maintenance/`, restart the NPM container
so the new bind-mounts take effect and ask nginx to re-parse its config:

```bash
sudo -u deploy /usr/bin/docker compose \
    -f /var/iri/code/docker-compose.yml --profile prod \
    up -d npm

sudo -u deploy /usr/bin/docker compose \
    -f /var/iri/code/docker-compose.yml --profile prod \
    exec npm nginx -t          # must report "syntax is ok"
```

Then simulate an upstream failure to confirm the page is wired up:

```bash
sudo -u deploy /usr/bin/docker compose \
    -f /var/iri/code/docker-compose.yml --profile prod \
    stop frontend

curl -i https://profit-base.online/                  # expect HTTP/1.1 503 + HTML
curl -i -H 'Accept: application/json' \
        https://profit-base.online/api/v1/missions   # expect HTTP/1.1 503 + JSON

sudo -u deploy /usr/bin/docker compose \
    -f /var/iri/code/docker-compose.yml --profile prod \
    start frontend
```

The page is intentionally not scoped per virtual host — any proxy host behind
NPM (including `keycloak.profit-base.online`) will fall back to the same screen if
its upstream ever serves a `5xx`. The wording is kept generic ("System
maintenance") so it reads correctly for both.

---

## Keycloak behind NPM over HTTPS

Keycloak no longer serves plain HTTP in production. The `keycloak` service starts with
`--http-enabled=false --https-port=18443`, so **both** edges that reach it are now TLS:

- **NPM → Keycloak** — `nginx-proxy-manager` terminates the public Let's Encrypt cert for
  `keycloak.profit-base.online` and **re-encrypts** to `https://keycloak:18443` on the internal
  `net-proxy-keycloak` network.
- **backend → Keycloak** — the scheduled user sync (`KeycloakService`) reaches the same connector
  directly over the isolated `net-backend-keycloak` network via `KEYCLOAK_ADMIN_URL=https://keycloak:18443`,
  pinning the shared self-signed cert through the `keycloak-trust` SSL bundle.

The management/health interface (port 9000) is deliberately kept on **HTTP**
(`--http-management-scheme=http`): the Quarkus image ships no TLS-capable CLI client (only `java`),
and the container `HEALTHCHECK` opens a plain-HTTP socket to `/health/ready` via bash `/dev/tcp`.
That port is container-loopback only — it is never published nor placed on a proxy network.

### Prerequisite — the shared keystore must carry `dns:keycloak`

The backend keeps **hostname verification on** for the admin call (the synchronous JDK `HttpClient`
cannot disable it reliably per-client), so the cert presented on `https://keycloak:18443` must list
`keycloak` in its SAN. The shared `keystore.p12` historically did **not**. Regenerate it (this is
the only cert the internal services present to each other; NPM's public Let's Encrypt cert is
separate and untouched):

The prod host has **no JDK** (containers only), so there is no host `keytool`.
Don't install one — the backend image is `eclipse-temurin:25-jre-alpine`, which
bundles `keytool`; run it as a throwaway container with the secrets dir mounted
read-write:

```bash
cd /var/iri/code

# Keystore password (= the value in /var/iri/code/.env) and the backend image
# already on the host. Falls back to the upstream base if the stack image can't
# be resolved.
PW=$(sudo grep -E '^SERVER_SSL_KEY_STORE_PASSWORD=' .env | cut -d= -f2-)
IMG=$(sudo docker compose --profile prod config --images | grep basetool-backend | head -1)
IMG=${IMG:-eclipse-temurin:25-jre-alpine}

# Back up + remove the old keystore so keytool creates a fresh one (it refuses
# otherwise: alias 'basetool' already exists).
sudo cp /var/iri/secrets/keystore.p12 /var/iri/secrets/keystore.p12.bak
sudo rm -f /var/iri/secrets/keystore.p12

# Generate via the JRE inside the image: --user 0 so it can write into the
# root-owned secrets dir; --entrypoint keytool overrides the app entrypoint.
sudo docker run --rm --user 0 --entrypoint keytool -v /var/iri/secrets:/work "$IMG" \
  -genkeypair -alias basetool -storetype PKCS12 -keystore /work/keystore.p12 \
  -storepass "$PW" -keypass "$PW" \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=basetool, OU=IRIDIUM, O=DAS KARTELL, C=DE" \
  -ext "san=dns:localhost,ip:127.0.0.1,dns:backend,dns:frontend,dns:ingest,dns:keycloak"

# World-readable (0644) so BOTH the uid-10001 JVM services AND the uid-1000
# Keycloak image can read it (see "5.2 PKCS12 keystore"). 0640 makes Keycloak
# crash with AccessDeniedException.
sudo chown root:10001 /var/iri/secrets/keystore.p12
sudo chmod 0644        /var/iri/secrets/keystore.p12

# Confirm the SAN carries dns:keycloak before restarting anything.
sudo docker run --rm --entrypoint keytool -v /var/iri/secrets:/work "$IMG" \
  -list -v -keystore /work/keystore.p12 -storepass "$PW" | grep -i keycloak
```

### Host steps

1. **Ship the release that contains this change.** Cut + promote a version whose `basetool-backend`
   image carries the `keycloak-trust` bundle and the `KeycloakService` TLS wiring (see
   [Normal deploy flow](#normal-deploy-flow)). An *old* backend image pointed at the HTTPS admin URL
   would fail the handshake.
2. **Update the compose file on the host** — the new `keycloak` command/volumes and the backend
   `KEYCLOAK_ADMIN_URL` live in `docker-compose.yml`, which is copied manually:

   ```bash
   sudo cp docker-compose.yml /var/iri/code/docker-compose.yml
   sudo chown deploy:docker   /var/iri/code/docker-compose.yml
   ```
3. **Regenerate the keystore** with `dns:keycloak` (previous section).
4. **Reconfigure the NPM proxy host** for `keycloak.profit-base.online` (NPM admin UI on
   `127.0.0.1:10081`): set **Forward Scheme = `https`** and **Forward Port = `18443`** (Forward
   Hostname stays `keycloak`). nginx does **not** verify the upstream certificate by default, so the
   self-signed cert is accepted with no extra toggle. The public SSL tab (Let's Encrypt) is unchanged.
5. **Apply.** First re-create the services whose compose definition / image changed (`keycloak`
   recreates for the new command + keystore mount; `backend` for the new image + `KEYCLOAK_ADMIN_URL`):

   ```bash
   sudo -u deploy /usr/bin/docker compose \
       -f /var/iri/code/docker-compose.yml --profile prod \
       up -d keycloak backend
   ```

   Then **restart `frontend` and `ingest`** so they reload the regenerated `keystore.p12`. This is
   easy to miss: the keystore is a bind mount, so `up -d` does **not** recreate these two — but the
   shared cert is loaded once at JVM start, both as each service's own HTTPS server cert *and* as its
   `backend-trust` truststore. Without the restart they keep pinning the **old** cert and every
   frontend/ingest → backend call fails with `PKIX path building failed` once the backend presents the
   new one:

   ```bash
   sudo -u deploy /usr/bin/docker compose \
       -f /var/iri/code/docker-compose.yml --profile prod \
       restart frontend ingest
   ```

   The NPM proxy-host change (step 4) is applied live by nginx on save; restarting the `npm` container
   is not required.

### Verify

```bash
# Keycloak is healthy (proves the HTTP management healthcheck still works after the HTTPS flip).
sudo -u deploy /usr/bin/docker compose -f /var/iri/code/docker-compose.yml --profile prod ps keycloak

# Public OIDC discovery still resolves through NPM (NPM → keycloak:18443 re-encryption works).
curl -fsS https://keycloak.profit-base.online/realms/iri/.well-known/openid-configuration >/dev/null && echo OK

# Backend user sync succeeds over TLS — no recurring "Failed to fetch users from Keycloak".
sudo -u deploy /usr/bin/docker compose -f /var/iri/code/docker-compose.yml --profile prod \
    logs --since 5m backend | grep -i "fetch users from keycloak" || echo "no sync errors"
```

A `PKIX path building failed` or `No subject alternative DNS name matching keycloak` in the backend
log means the keystore was not regenerated with `dns:keycloak` — redo the prerequisite. As an
emergency fallback only, revert `KEYCLOAK_ADMIN_URL` to `http://keycloak:18080` **and** drop
`--http-enabled=false` from the `keycloak` command, then re-`up`; fix the cert and switch back.

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

|                         Symptom                          |                       Where to look                       |                                                                                           Common cause                                                                                           |
|----------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Timer fires but image never updates                      | `journalctl -u iri-deploy.service`                        | `:stable` not yet promoted. Run `gh workflow run promote.yml -f version=...`.                                                                                                                    |
| `docker login` fails                                     | `/var/log/iri-deploy.log`                                 | Expired or revoked PAT. See *Token rotation*.                                                                                                                                                    |
| Health check times out                                   | `docker compose ps`, `docker logs <service>`              | New version broken; the script auto-rolls back. Inspect the rolled-back container's logs for the root cause.                                                                                     |
| Service stays "unhealthy" after rollback                 | `docker logs db-backend` etc.                             | Infrastructure-side problem (disk full, DB corruption). Not caused by the deploy.                                                                                                                |
| Keystore mount fails / Keycloak `AccessDeniedException`  | `docker compose logs keycloak`, `ls -la /var/iri/secrets` | Keystore missing or mode too strict. It must be `0644` — the JVM services run as uid 10001 but Keycloak runs as uid 1000, so `0640` denies Keycloak. `chmod 0644 /var/iri/secrets/keystore.p12`. |
| `IRI_KEYSTORE_HOST_PATH` referenced but file not present | `.env`                                                    | Sync `.env` and `/var/iri/secrets/keystore.p12` between path and contents.                                                                                                                       |
| Compose pulls but does not restart                       | journald log                                              | All target digests match the last-deployed digests — that is the idempotent no-op path. Force-clear `/var/lib/iri/last-deployed.digests` if you want a forced restart.                           |

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
  keystore lives only on the server, in a root-owned `0644` file
  (world-readable so both the uid-10001 JVM services and the uid-1000
  Keycloak image can read the shared self-signed cert; root ownership still
  blocks the deploy user from rewriting it).

