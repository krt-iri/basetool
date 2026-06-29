#!/bin/bash
# =============================================================================
# Profit Basetool — Server-side deploy script
#
# Pulls the production backend + frontend + ingest images AND the host config
# bundle (basetool-config: docker-compose.yml + maintenance page + Keycloak
# theme) from GHCR (the version tag defaults to `:stable`, which is moved
# atomically by the `promote.yml` GitHub Actions workflow), resolves them to
# immutable digests, applies them via docker-compose with `--wait`, and rolls
# back to the previous digest set AND the previous config tree if the
# health-check fails within IRI_HEALTH_TIMEOUT seconds.
#
# The config bundle travels the SAME pull-only, digest-pinned, deliberately
# promoted GHCR channel as the app images (see docs/adr/0049-*), so a promoted
# compose change — e.g. a bumped redis/npm image pin — reaches the host and is
# applied automatically, with no manual `cp docker-compose.yml` or hand-run
# `docker compose up -d`. A postgres/Keycloak image change is the one carve-out:
# it is operator-gated (stateful migration / provider+keystore choreography),
# never auto-applied. deploy.sh and the systemd units are NOT part of the bundle
# (self-update hazard) and stay a manual bootstrap concern.
#
# Invoked periodically by the `iri-deploy.timer` systemd unit, or manually:
#   sudo -u deploy /var/iri/code/scripts/deploy.sh                  # apply :stable
#   sudo -u deploy /var/iri/code/scripts/deploy.sh --tag 1.4.2      # pin a specific version
#   sudo -u deploy /var/iri/code/scripts/deploy.sh --check-only     # dry-run
#   sudo -u deploy /var/iri/code/scripts/deploy.sh --force          # retry a backed-off target now
#
# State files (rewritten on every deploy):
#   /var/lib/iri/current-digest-pin.yml    compose override pinning the live
#                                          backend/frontend/ingest image digests;
#                                          used on every subsequent `up` so a tag
#                                          flip in GHCR does NOT silently move
#                                          the running stack underneath us.
#   /var/lib/iri/previous-digest-pin.yml   the prior pin, restored on rollback.
#   /var/lib/iri/last-deployed.digests     idempotence marker — when ALL target
#                                          digests (backend|frontend|ingest plus
#                                          the config-bundle digest) match this
#                                          file, the script exits 0 without
#                                          restarting. The config digest is part
#                                          of the marker so a config-only change
#                                          (e.g. a redis pin bump) is NOT skipped.
#   /var/lib/iri/failed.digests            a digest set whose health check
#                                          failed, plus a failure counter, so
#                                          the SAME broken target is retried
#                                          with exponential backoff instead of
#                                          on every tick. Cleared on a
#                                          successful deploy or when a new
#                                          digest is promoted to the tag.
#   /var/lib/iri/config-stage/             scratch dir the promoted config bundle
#                                          is extracted into before being copied
#                                          into /var/iri/code (never applied in
#                                          place).
#   /var/lib/iri/config-previous/          snapshot of the live config tree taken
#                                          before a config swap; restored on
#                                          rollback (the config analogue of
#                                          previous-digest-pin.yml).
#   /var/lib/iri/config-blocked.marker     a target whose config carries an
#                                          operator-gated postgres/Keycloak image
#                                          change. Recorded so the carve-out
#                                          alerts once, then skips subsequent
#                                          ticks quietly until a new promotion or
#                                          a --force run. Cleared on apply.
#
# Locking: a single `flock` on /var/lock/iri-deploy.lock prevents the systemd
# timer and a manual invocation from racing each other.
# =============================================================================

set -euo pipefail

# --- Defaults / paths -------------------------------------------------------
COMPOSE_DIR="${IRI_COMPOSE_DIR:-/var/iri/code}"
STATE_DIR="${IRI_STATE_DIR:-/var/lib/iri}"
LOCKFILE="${IRI_LOCKFILE:-/var/lock/iri-deploy.lock}"
TOKEN_FILE="${IRI_GHCR_TOKEN_FILE:-/etc/iri/ghcr-pull-token}"
HEALTH_TIMEOUT="${IRI_HEALTH_TIMEOUT:-180}"

REGISTRY="${IRI_REGISTRY:-ghcr.io}"
NAMESPACE="${IRI_IMAGE_NAMESPACE:-krt-profit}"
GHCR_USERNAME="${IRI_GHCR_USERNAME:-deploy-bot}"

PROFILE=prod
TARGET_TAG=stable
CHECK_ONLY=false
FORCE=false

# Bad-digest backoff: after a health-check failure the SAME target digest pair
# is retried with an exponential backoff (BACKOFF_BASE seconds, doubling per
# consecutive failure, capped at BACKOFF_MAX) instead of on every timer tick.
# Keyed to the digest pair, so a freshly promoted (fixed) image still deploys
# immediately; `--force` bypasses the wait.
BACKOFF_BASE="${IRI_BACKOFF_BASE:-600}"
BACKOFF_MAX="${IRI_BACKOFF_MAX:-21600}"

# --- CLI args ---------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      [[ -n "${2:-}" ]] || { echo "FATAL: --tag requires a value" >&2; exit 1; }
      TARGET_TAG="$2"
      shift 2
      ;;
    --check-only)
      CHECK_ONLY=true
      shift
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: deploy.sh [--tag <ref>] [--check-only] [--force]

Options:
  --tag <ref>     Image tag/ref to deploy. Default: stable
                  Examples: stable, latest, 1.4.2, sha-abc1234
  --check-only    Resolve digests but do not apply (dry-run).
  --force         Bypass the bad-digest backoff and retry a previously failed
                  target now (e.g. after fixing an environmental cause).
  -h, --help      Show this help.

Environment overrides (all optional, sensible defaults shown):
  IRI_COMPOSE_DIR=/var/iri/code
  IRI_STATE_DIR=/var/lib/iri
  IRI_LOCKFILE=/var/lock/iri-deploy.lock
  IRI_GHCR_TOKEN_FILE=/etc/iri/ghcr-pull-token
  IRI_HEALTH_TIMEOUT=180
  IRI_BACKOFF_BASE=600     (first retry delay after a failed target, seconds)
  IRI_BACKOFF_MAX=21600    (cap for the exponential backoff, seconds)
  IRI_REGISTRY=ghcr.io
  IRI_IMAGE_NAMESPACE=krt-profit
  IRI_GHCR_USERNAME=deploy-bot
  DOCKER_CONFIG=/var/lib/iri/.docker   (where `docker login` writes its
                                        credentials.json; defaults to a
                                        per-script location under STATE_DIR
                                        because the deploy user has no \$HOME)
USAGE
      exit 0
      ;;
    *)
      echo "FATAL: unknown argument: $1 (try --help)" >&2
      exit 1
      ;;
  esac
done

# --- Helpers ----------------------------------------------------------------
log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  log "FATAL: $*"
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "required file missing: $1"
}

# Mirror a source directory onto a destination, propagating deletions WITHIN the
# subtree only. Used to apply the bundled maintenance-page / Keycloak-theme trees
# without ever touching anything outside them. Prefers rsync; falls back to a
# clean re-copy when rsync is absent (it is not a hard host dependency).
#
# Runs as the unprivileged `deploy` user, so it deliberately does NOT preserve
# owner/group. `rsync -a` (= -rlptgoD) and `cp -a` (= --preserve=all) both try to
# `chgrp`/`chown` every entry, which fails ("Operation not permitted") the moment
# any file in the live tree is owned by someone else — e.g. a root-owned bootstrap
# copy of keycloak-theme. `rsync -rlpt` / `cp -R` keep recursion, symlinks, perms
# and times (all the maintenance/theme assets need) without ever touching
# ownership, so the mirror succeeds as long as the destination dirs are writable.
mirror_dir() {
  local src="$1" dst="$2"
  if command -v rsync >/dev/null 2>&1; then
    rsync -rlpt --delete "${src}/" "${dst}/"
  else
    rm -rf "${dst}"
    install -d "${dst%/*}"
    cp -R "${src}" "${dst}"
  fi
}

# Extract the promoted config bundle (/config inside the scratch basetool-config
# image) into a staging dir. The image has no entrypoint/command, so `docker
# create` needs a placeholder argument; the container is never started —
# `docker cp` reads straight from its filesystem layer.
extract_config_bundle() {
  local ref="$1" dest="$2" cid
  rm -rf "${dest}"
  install -d -m 0755 "${dest}"
  cid="$(docker create "${ref}" /bundle 2>/dev/null)" \
    || fail "cannot create container from config image ${ref}"
  if ! docker cp "${cid}:/config/." "${dest}/" >/dev/null 2>&1; then
    docker rm -f "${cid}" >/dev/null 2>&1 || true
    fail "cannot extract /config from config image ${ref}"
  fi
  docker rm -f "${cid}" >/dev/null 2>&1 || true
}

# Fail loudly if a staged config bundle smuggled in a host secret. The bundle is
# built from an explicit COPY allowlist and .dockerignore bars secrets from the
# build context, but this is the last gate before the tree is copied onto the
# host — defence in depth against a future Dockerfile edit widening the COPY.
assert_no_secrets() {
  local dir="$1" name
  for name in .env keystore.p12 realm-export.json; do
    if find "${dir}" -name "${name}" -print -quit 2>/dev/null | grep -q .; then
      fail "SECURITY: promoted config bundle contains forbidden file '${name}' — aborting before apply"
    fi
  done
  if [[ -d "${dir}/keycloak/providers" ]]; then
    fail "SECURITY: promoted config bundle contains keycloak/providers — aborting before apply"
  fi
}

# Emit the postgres + Keycloak image pins of a compose file, normalised and
# sorted. These are the stateful/choreographed images whose change must be
# operator-gated (PGDATA major migration; Keycloak provider+keystore dance);
# everything else (redis, npm) is safe to auto-apply via a declarative `up -d`.
infra_image_pins() {
  # `|| true`: a compose file with no match makes grep exit 1, which under
  # `set -o pipefail` would abort the surrounding command substitution. An empty
  # result is the correct "no stateful-infra pins seen" answer here.
  grep -Eo 'image:[[:space:]]*(postgres:[^[:space:]]+|quay\.io/keycloak/keycloak:[^[:space:]]+)' "$1" \
    | sed -E 's/image:[[:space:]]*//' | sort -u || true
}

# Snapshot the live config tree (the allowlisted paths) into a directory so the
# rollback path can restore the exact compose the previous digest pin expects.
snapshot_config_tree() {
  local dst="$1"
  rm -rf "${dst}"
  install -d -m 0755 "${dst}"
  [[ -f "${COMPOSE_DIR}/docker-compose.yml" ]] \
    && cp -a "${COMPOSE_DIR}/docker-compose.yml" "${dst}/docker-compose.yml"
  if [[ -d "${COMPOSE_DIR}/docker/maintenance" ]]; then
    install -d "${dst}/docker"
    cp -a "${COMPOSE_DIR}/docker/maintenance" "${dst}/docker/maintenance"
  fi
  [[ -d "${COMPOSE_DIR}/keycloak-theme" ]] \
    && cp -a "${COMPOSE_DIR}/keycloak-theme" "${dst}/keycloak-theme"
  return 0
}

# Copy the allowlisted config paths from a source tree onto the host. The compose
# file is replaced atomically (write a temp, then rename → new inode) so a
# concurrent reader never sees a half-written file; the asset trees are mirrored
# within their own subtree only.
apply_config_tree() {
  local src="$1" dst="$2"
  install -m 0644 "${src}/docker-compose.yml" "${dst}/.docker-compose.yml.tmp"
  mv -f "${dst}/.docker-compose.yml.tmp" "${dst}/docker-compose.yml"
  if [[ -d "${src}/docker/maintenance" ]]; then
    install -d "${dst}/docker"
    mirror_dir "${src}/docker/maintenance" "${dst}/docker/maintenance"
  fi
  if [[ -d "${src}/keycloak-theme" ]]; then
    mirror_dir "${src}/keycloak-theme" "${dst}/keycloak-theme"
  fi
}

# --- Pre-flight -------------------------------------------------------------
require_file "${COMPOSE_DIR}/docker-compose.yml"
require_file "${COMPOSE_DIR}/.env"
require_file "${TOKEN_FILE}"

# Pull the host-side keystore path from .env so the pre-flight check covers
# the actual mount source, not just our hard-coded default. The fall-back
# matches the default in docker-compose.yml's volume entry.
KEYSTORE_HOST_PATH="$(grep -E '^IRI_KEYSTORE_HOST_PATH=' "${COMPOSE_DIR}/.env" 2>/dev/null \
                       | tail -n1 | cut -d= -f2- | tr -d '"' || true)"
KEYSTORE_HOST_PATH="${KEYSTORE_HOST_PATH:-/var/iri/secrets/keystore.p12}"
require_file "${KEYSTORE_HOST_PATH}"

mkdir -p "${STATE_DIR}"

# Pin DOCKER_CONFIG BEFORE the first docker invocation. The `deploy` user is
# created with `useradd --no-create-home`, so $HOME=/home/deploy does not
# exist on disk; under the systemd unit's `ProtectHome=true` the directory
# is additionally an inaccessible empty tmpfs mount. Either situation makes
# Docker CLI's default config-discovery path under $HOME/.docker fail —
# `docker login` would error out with `mkdir /home/deploy: permission
# denied`, and even the cheaper `docker compose version --short` pre-flight
# probe below exits non-zero because the compose plugin tries to read its
# config from the unreachable $HOME on startup.
#
# Pinning DOCKER_CONFIG into STATE_DIR sidesteps both problems: the path is
# already in the systemd unit's ReadWritePaths set, persists the credential
# between timer ticks, and stays under the deploy user's exclusive 0700
# ownership. Must come BEFORE the docker compose version check below — the
# order is load-bearing.
export DOCKER_CONFIG="${DOCKER_CONFIG:-${STATE_DIR}/.docker}"
install -d -m 0700 "${DOCKER_CONFIG}"

# Compose v2 ships with Docker Engine ≥ 20.10.13 as `docker compose`; the
# `--wait` flag landed in 2.1.0. Fail fast on older installs rather than
# discovering it during `up`.
if ! docker compose version --short >/dev/null 2>&1; then
  fail "docker compose v2 not available; install Docker Engine ≥ 23.x"
fi

PIN_FILE_CURRENT="${STATE_DIR}/current-digest-pin.yml"
PIN_FILE_PREVIOUS="${STATE_DIR}/previous-digest-pin.yml"
LAST_DEPLOYED_FILE="${STATE_DIR}/last-deployed.digests"
FAILED_FILE="${STATE_DIR}/failed.digests"
CONFIG_STAGE_DIR="${STATE_DIR}/config-stage"
CONFIG_PREVIOUS_DIR="${STATE_DIR}/config-previous"
CONFIG_BLOCKED_FILE="${STATE_DIR}/config-blocked.marker"

# --- Lock -------------------------------------------------------------------
exec 200>"${LOCKFILE}"
if ! flock -n 200; then
  log "another deploy is in progress (lock: ${LOCKFILE}); exiting"
  exit 0
fi

# --- Authenticate to GHCR ---------------------------------------------------
log "logging in to ${REGISTRY} as ${GHCR_USERNAME}"
if ! docker login "${REGISTRY}" \
       --username "${GHCR_USERNAME}" \
       --password-stdin < "${TOKEN_FILE}" >/dev/null 2>&1; then
  fail "docker login to ${REGISTRY} failed — check ${TOKEN_FILE} (scope: read:packages)"
fi

# --- Resolve target digests -------------------------------------------------
BACKEND_IMAGE="${REGISTRY}/${NAMESPACE}/basetool-backend"
FRONTEND_IMAGE="${REGISTRY}/${NAMESPACE}/basetool-frontend"
INGEST_IMAGE="${REGISTRY}/${NAMESPACE}/basetool-ingest"
CONFIG_IMAGE="${REGISTRY}/${NAMESPACE}/basetool-config"

resolve_digest() {
  # buildx imagetools resolves a tag to its manifest digest without pulling
  # the image. Works for multi-arch lists (returns the index digest) and for
  # single-platform manifests alike.
  local ref="$1"
  docker buildx imagetools inspect "${ref}" --format '{{.Manifest.Digest}}' 2>/dev/null
}

log "resolving ${TARGET_TAG} → digest"
BACKEND_DIGEST="$(resolve_digest "${BACKEND_IMAGE}:${TARGET_TAG}")" \
  || fail "cannot resolve ${BACKEND_IMAGE}:${TARGET_TAG} (tag missing or no GHCR access)"
FRONTEND_DIGEST="$(resolve_digest "${FRONTEND_IMAGE}:${TARGET_TAG}")" \
  || fail "cannot resolve ${FRONTEND_IMAGE}:${TARGET_TAG} (tag missing or no GHCR access)"
INGEST_DIGEST="$(resolve_digest "${INGEST_IMAGE}:${TARGET_TAG}")" \
  || fail "cannot resolve ${INGEST_IMAGE}:${TARGET_TAG} (tag missing or no GHCR access)"

# Config artifact is resolved BEST-EFFORT: a host running this script before the
# first config:stable promotion (or a transient hiccup on just this one tag) must
# not brick the app deploy loop. When absent, fall back to the legacy app-only
# behaviour for this tick (3-field marker, no config staging).
CONFIG_DIGEST="$(resolve_digest "${CONFIG_IMAGE}:${TARGET_TAG}")" || CONFIG_DIGEST=""

log "target backend  ${BACKEND_DIGEST}"
log "target frontend ${FRONTEND_DIGEST}"
log "target ingest   ${INGEST_DIGEST}"

# Single whitespace-free token (digests carry no spaces), so the failed.digests
# `read -r REC_MARKER REC_COUNT REC_EPOCH` parsing below stays a single field.
# The config digest is the 4th field so a config-only change still moves the
# marker (and a stale app-only deploy never silently drops a promoted compose).
if [[ -n "${CONFIG_DIGEST}" ]]; then
  log "target config   ${CONFIG_DIGEST}"
  EXPECTED_MARKER="${BACKEND_DIGEST}|${FRONTEND_DIGEST}|${INGEST_DIGEST}|${CONFIG_DIGEST}"
else
  log "target config   unavailable (${CONFIG_IMAGE}:${TARGET_TAG} not resolvable) — app-only deploy this tick"
  EXPECTED_MARKER="${BACKEND_DIGEST}|${FRONTEND_DIGEST}|${INGEST_DIGEST}"
fi

# Decide whether the *config* component changed since the last successful deploy
# — used to skip config re-staging on an app-only deploy. An older 3-field marker
# (pre-config-artifact, or a tick where config was unavailable) has no config
# digest → treat as changed so the first tick that sees the artifact stages it.
CONFIG_CHANGED=false
if [[ -n "${CONFIG_DIGEST}" ]]; then
  CONFIG_CHANGED=true
  if [[ -f "${LAST_DEPLOYED_FILE}" ]]; then
    LAST_MARKER="$(cat "${LAST_DEPLOYED_FILE}")"
    if [[ "${LAST_MARKER}" == *"|"*"|"*"|"* ]] \
       && [[ "${LAST_MARKER##*|}" == "${CONFIG_DIGEST}" ]]; then
      CONFIG_CHANGED=false
    fi
  fi
fi

# --- Idempotence check ------------------------------------------------------
if [[ -f "${LAST_DEPLOYED_FILE}" ]] \
   && grep -qFx "${EXPECTED_MARKER}" "${LAST_DEPLOYED_FILE}"; then
  log "no change — already at target digests"
  exit 0
fi

if [[ "${CHECK_ONLY}" == "true" ]]; then
  log "check-only: would deploy"
  exit 0
fi

# --- Bad-digest backoff -----------------------------------------------------
# A target whose health check failed is NOT retried on every 5-minute tick:
# without this, a broken `:stable` (or a transient failure) re-applies and rolls
# back forever, each cycle taking the stack offline for the HEALTH_TIMEOUT
# window. We back off exponentially per consecutive failure of the SAME digest
# pair; promoting a new (fixed) image changes EXPECTED_MARKER, clears the record
# and deploys at once, so only re-attempts of the known-bad pair are throttled.
if [[ -f "${FAILED_FILE}" ]]; then
  read -r REC_MARKER REC_COUNT REC_EPOCH _ < "${FAILED_FILE}" || true
  if [[ "${REC_MARKER:-}" != "${EXPECTED_MARKER}" ]] \
     || ! [[ "${REC_COUNT:-}" =~ ^[0-9]+$ ]] \
     || ! [[ "${REC_EPOCH:-}" =~ ^[0-9]+$ ]]; then
    # Target moved to a new promotion, or the record is stale/corrupt: drop it
    # and deploy normally.
    rm -f "${FAILED_FILE}"
  elif [[ "${FORCE}" == "true" ]]; then
    log "target previously failed ${REC_COUNT}x; --force given — retrying now"
  else
    if (( 10#${REC_COUNT} > 20 )); then
      backoff="${BACKOFF_MAX}"
    else
      backoff=$(( BACKOFF_BASE * (2 ** (10#${REC_COUNT} - 1)) ))
      if (( backoff > BACKOFF_MAX )); then
        backoff="${BACKOFF_MAX}"
      fi
    fi
    elapsed=$(( $(date +%s) - 10#${REC_EPOCH} ))
    if (( elapsed < backoff )); then
      log "target failed ${REC_COUNT}x; in backoff window (${elapsed}s/${backoff}s) — skipping this tick (promote a fixed image or pass --force)"
      exit 0
    fi
    log "target failed ${REC_COUNT}x; backoff of ${backoff}s elapsed — retrying"
  fi
fi

# --- Save rollback anchor + write new pin -----------------------------------
[[ -f "${PIN_FILE_CURRENT}" ]] && cp "${PIN_FILE_CURRENT}" "${PIN_FILE_PREVIOUS}"

cat > "${PIN_FILE_CURRENT}" <<EOF
# Auto-generated by scripts/deploy.sh. Do not edit by hand — it is rewritten
# on every deploy. Pinning to the exact image digests makes a subsequent
# \`:stable\` tag flip in GHCR a no-op until the next deploy.sh run.
services:
  backend:
    image: ${BACKEND_IMAGE}@${BACKEND_DIGEST}
  frontend:
    image: ${FRONTEND_IMAGE}@${FRONTEND_DIGEST}
  ingest:
    image: ${INGEST_IMAGE}@${INGEST_DIGEST}
EOF

# --- Deliver promoted host config -------------------------------------------
# The compose file and its sibling host config (NPM maintenance page, Keycloak
# theme) ride the SAME promoted, digest-pinned GHCR channel as the app images.
# Only (re)stage them when the promoted config digest actually moved, so an
# app-only promotion stays byte-for-byte the legacy path.
if [[ "${CONFIG_CHANGED}" == "true" ]]; then
  log "config changed → staging ${CONFIG_IMAGE}@${CONFIG_DIGEST}"
  extract_config_bundle "${CONFIG_IMAGE}@${CONFIG_DIGEST}" "${CONFIG_STAGE_DIR}"
  require_file "${CONFIG_STAGE_DIR}/docker-compose.yml"
  assert_no_secrets "${CONFIG_STAGE_DIR}"

  # Carve-out: a postgres / Keycloak IMAGE change is a stateful, choreographed
  # upgrade (PGDATA major migration; Keycloak provider+keystore dance) that a
  # blind `up -d` would break and the health-gate would then roll back on a
  # 5-minute loop. Refuse to auto-apply it; record the target so we alert ONCE
  # and then skip quietly until a new promotion or an operator --force. The
  # operator runs the documented manual upgrade, then re-runs with --force.
  OLD_INFRA="$(infra_image_pins "${COMPOSE_DIR}/docker-compose.yml")"
  NEW_INFRA="$(infra_image_pins "${CONFIG_STAGE_DIR}/docker-compose.yml")"
  if [[ "${OLD_INFRA}" != "${NEW_INFRA}" ]] && [[ "${FORCE}" != "true" ]]; then
    if [[ -f "${CONFIG_BLOCKED_FILE}" ]] && grep -qFx "${EXPECTED_MARKER}" "${CONFIG_BLOCKED_FILE}"; then
      log "stateful-infra upgrade still operator-gated for this target; skipping tick (run the manual upgrade then --force)"
      exit 0
    fi
    echo "${EXPECTED_MARKER}" > "${CONFIG_BLOCKED_FILE}"
    log "CARVE-OUT: postgres/Keycloak image pin changed — refusing to auto-apply a stateful-infra upgrade"
    log "  old: $(printf '%s' "${OLD_INFRA}" | tr '\n' ' ')"
    log "  new: $(printf '%s' "${NEW_INFRA}" | tr '\n' ' ')"
    log "  perform the documented manual upgrade (docs/deployment.md → Stateful-infra upgrades), then: deploy.sh --force"
    exit 3
  fi
  rm -f "${CONFIG_BLOCKED_FILE}"

  # Snapshot the live config tree as the rollback anchor, then swap in the new.
  snapshot_config_tree "${CONFIG_PREVIOUS_DIR}"
  apply_config_tree "${CONFIG_STAGE_DIR}" "${COMPOSE_DIR}"
  [[ -f "${COMPOSE_DIR}/.env" ]] \
    || fail "POST-APPLY: ${COMPOSE_DIR}/.env vanished after config swap — aborting before up"
  log "config applied"
fi

# --- Apply ------------------------------------------------------------------
cd "${COMPOSE_DIR}"

# Only pre-pull the images this deploy actually moves (backend + frontend +
# ingest from GHCR). The third-party infra images (keycloak/postgres/redis/npm)
# are pinned by digest and change only on a deliberate compose edit; pulling
# them here would make every deploy hostage to a transient outage of a
# third-party registry (e.g. a quay.io 502/504 on the Keycloak manifest aborting
# the whole `pull` under `set -e`, before `up` ever runs). The `up -d` below
# still pulls any infra image that is genuinely missing locally, so a real
# digest bump is rolled forward — an already-present pinned image is simply
# reused offline.
log "pulling images"
docker compose \
  -f docker-compose.yml \
  -f "${PIN_FILE_CURRENT}" \
  --profile "${PROFILE}" \
  pull --quiet backend frontend ingest

log "applying (timeout ${HEALTH_TIMEOUT}s)"
if docker compose \
     -f docker-compose.yml \
     -f "${PIN_FILE_CURRENT}" \
     --profile "${PROFILE}" \
     up -d \
        --no-build \
        --remove-orphans \
        --wait \
        --wait-timeout "${HEALTH_TIMEOUT}"; then

  echo "${EXPECTED_MARKER}" > "${LAST_DEPLOYED_FILE}"
  rm -f "${FAILED_FILE}" "${CONFIG_BLOCKED_FILE}"
  log "deploy successful"

  # Best-effort prune of dangling images older than 30 days. Restricted via
  # `until=720h` to avoid wiping the just-pulled images we may still need to
  # roll back to. `|| true` because a stuck container ref can transiently
  # block a prune and we should not fail the deploy over it.
  docker image prune --force --filter "until=720h" >/dev/null 2>&1 || true
  exit 0
fi

# --- Rollback on health failure --------------------------------------------
log "health check failed within ${HEALTH_TIMEOUT}s — rolling back"

# Record this failure so subsequent ticks back off this exact (broken) digest
# pair instead of re-applying it every 5 minutes (see the backoff block above).
FAIL_COUNT=1
if [[ -f "${FAILED_FILE}" ]]; then
  read -r PREV_MARKER PREV_COUNT _ < "${FAILED_FILE}" || true
  if [[ "${PREV_MARKER:-}" == "${EXPECTED_MARKER}" ]] && [[ "${PREV_COUNT:-}" =~ ^[0-9]+$ ]]; then
    FAIL_COUNT=$(( 10#${PREV_COUNT} + 1 ))
  fi
fi
printf '%s %d %d\n' "${EXPECTED_MARKER}" "${FAIL_COUNT}" "$(date +%s)" > "${FAILED_FILE}"
log "recorded health-check failure #${FAIL_COUNT} for this target; next retry backs off"

# Revert the host config too (if this deploy swapped it), so the rolled-back
# stack reads the exact compose the previous digest pin expects. Done before the
# pin check so even the no-previous-pin exit leaves the config tree consistent.
if [[ "${CONFIG_CHANGED}" == "true" ]] && [[ -d "${CONFIG_PREVIOUS_DIR}" ]]; then
  log "restoring previous host config"
  apply_config_tree "${CONFIG_PREVIOUS_DIR}" "${COMPOSE_DIR}"
fi

if [[ ! -f "${PIN_FILE_PREVIOUS}" ]]; then
  log "no previous pin available — manual intervention required"
  exit 2
fi

cp "${PIN_FILE_PREVIOUS}" "${PIN_FILE_CURRENT}"

if docker compose \
     -f docker-compose.yml \
     -f "${PIN_FILE_CURRENT}" \
     --profile "${PROFILE}" \
     up -d \
        --no-build \
        --remove-orphans \
        --wait \
        --wait-timeout "${HEALTH_TIMEOUT}"; then
  log "rolled back to previous digest pin successfully"
else
  log "rollback ALSO failed — one or more target digests broken or environment problem"
fi

# Either way, this run failed → non-zero exit so the systemd unit reports
# `failed`, journalctl flags it, and any OnFailure= hook fires.
exit 1
