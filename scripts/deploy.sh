#!/bin/bash
# =============================================================================
# IRIDIUM Basetool — Server-side deploy script
#
# Pulls the production backend + frontend images from GHCR (the version tag
# defaults to `:stable`, which is moved atomically by the `promote.yml`
# GitHub Actions workflow), resolves them to immutable digests, applies them
# via docker-compose with `--wait`, and rolls back to the previous digest
# pair if the health-check fails within IRI_HEALTH_TIMEOUT seconds.
#
# Invoked periodically by the `iri-deploy.timer` systemd unit, or manually:
#   sudo -u deploy /var/iri/code/scripts/deploy.sh                  # apply :stable
#   sudo -u deploy /var/iri/code/scripts/deploy.sh --tag 1.4.2      # pin a specific version
#   sudo -u deploy /var/iri/code/scripts/deploy.sh --check-only     # dry-run
#
# State files (rewritten on every deploy):
#   /var/lib/iri/current-digest-pin.yml    compose override pinning the live
#                                          backend/frontend image digests; used
#                                          on every subsequent `up` so a tag
#                                          flip in GHCR does NOT silently move
#                                          the running stack underneath us.
#   /var/lib/iri/previous-digest-pin.yml   the prior pin, restored on rollback.
#   /var/lib/iri/last-deployed.digests     idempotence marker — when both
#                                          target digests match this file, the
#                                          script exits 0 without restarting.
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
NAMESPACE="${IRI_IMAGE_NAMESPACE:-krt-iri}"
GHCR_USERNAME="${IRI_GHCR_USERNAME:-deploy-bot}"

PROFILE=prod
TARGET_TAG=stable
CHECK_ONLY=false

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
    -h|--help)
      cat <<'USAGE'
Usage: deploy.sh [--tag <ref>] [--check-only]

Options:
  --tag <ref>     Image tag/ref to deploy. Default: stable
                  Examples: stable, latest, 1.4.2, sha-abc1234
  --check-only    Resolve digests but do not apply (dry-run).
  -h, --help      Show this help.

Environment overrides (all optional, sensible defaults shown):
  IRI_COMPOSE_DIR=/var/iri/code
  IRI_STATE_DIR=/var/lib/iri
  IRI_LOCKFILE=/var/lock/iri-deploy.lock
  IRI_GHCR_TOKEN_FILE=/etc/iri/ghcr-pull-token
  IRI_HEALTH_TIMEOUT=180
  IRI_REGISTRY=ghcr.io
  IRI_IMAGE_NAMESPACE=krt-iri
  IRI_GHCR_USERNAME=deploy-bot
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

# Compose v2 ships with Docker Engine ≥ 20.10.13 as `docker compose`; the
# `--wait` flag landed in 2.1.0. Fail fast on older installs rather than
# discovering it during `up`.
if ! docker compose version --short >/dev/null 2>&1; then
  fail "docker compose v2 not available; install Docker Engine ≥ 23.x"
fi

mkdir -p "${STATE_DIR}"

PIN_FILE_CURRENT="${STATE_DIR}/current-digest-pin.yml"
PIN_FILE_PREVIOUS="${STATE_DIR}/previous-digest-pin.yml"
LAST_DEPLOYED_FILE="${STATE_DIR}/last-deployed.digests"

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

log "target backend  ${BACKEND_DIGEST}"
log "target frontend ${FRONTEND_DIGEST}"

EXPECTED_MARKER="${BACKEND_DIGEST}|${FRONTEND_DIGEST}"

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
EOF

# --- Apply ------------------------------------------------------------------
cd "${COMPOSE_DIR}"

log "pulling images"
docker compose \
  -f docker-compose.yml \
  -f "${PIN_FILE_CURRENT}" \
  --profile "${PROFILE}" \
  pull --quiet

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
  log "rollback ALSO failed — both digests broken or environment problem"
fi

# Either way, this run failed → non-zero exit so the systemd unit reports
# `failed`, journalctl flags it, and any OnFailure= hook fires.
exit 1
