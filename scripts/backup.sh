#!/bin/bash
# =============================================================================
# Profit Basetool — consistent, encrypted, off-site backup
#
# Captures the full-restore backup surface and pushes it CLIENT-SIDE ENCRYPTED
# to a Nextcloud WebDAV target via restic (over an rclone remote). Runs nightly
# at 04:15 from iri-backup.timer (after the 04:00 vpn-restart), or manually:
#   sudo -u deploy /var/iri/code/scripts/backup.sh                # full run
#   sudo -u deploy /var/iri/code/scripts/backup.sh --no-quiesce   # online dump, zero downtime
#   sudo -u deploy /var/iri/code/scripts/backup.sh --skip-upload  # dump only, no restic push
#   sudo -u deploy /var/iri/code/scripts/backup.sh --dry-run      # show plan + snapshots, change nothing
#
# WHAT IS CAPTURED (the full-restore surface — docs/specs/backup-recovery.md,
# REQ-OPS-008/009):
#   * pg_dump -Fc of the backend database          (krt_basetool)
#   * pg_dump -Fc of the Keycloak database          (keycloak — the live source
#     of truth for realm/users/clients, NOT the sanitized realm-export.json)
#   * the nginx-proxy-manager state                 (/var/iri/npm = data SQLite +
#     letsencrypt) — proxy topology, access lists, certs
#   * host secrets needed to stand the stack up     (.env, keystore.p12,
#     realm-export.json, keycloak/providers)
#   NOT captured by design: Redis (sessions just re-login), logs, and the
#   WireGuard wg0.conf key (the operator backs that up out-of-band — REQ-OPS-010).
#
# CONSISTENCY (REQ-OPS-009): pg_dump alone is already a transactionally
# consistent snapshot, but to obtain one globally quiescent instant we briefly
# STOP the writer services (frontend, backend, ingest) for the DUMP only — NPM
# serves the existing maintenance page meanwhile — then restart them BEFORE the
# slow restic upload. The user-facing window is therefore the dump duration
# (seconds), never the upload. A trap guarantees the stack is restarted even if
# a dump step fails, so production is never left down.
#
# COORDINATION: acquires the SAME flock deploy.sh uses (/var/lock/iri-deploy.lock)
# so a 5-minute deploy tick cannot recreate containers mid-backup, and vice
# versa. The lock is released as soon as the writers are back up, so the slow
# upload never blocks a deploy.
#
# SECRETS: the restic repo password + rclone/Nextcloud app-password live in
# /etc/iri/backup.env (root-only), never in git and never in the .env config
# bundle (REQ-OPS-005, REQ-OPS-012). The staged plaintext dumps live under
# /var/iri/backup/staging and are removed on every exit.
# =============================================================================

set -euo pipefail

# --- Defaults / paths -------------------------------------------------------
COMPOSE_DIR="${IRI_COMPOSE_DIR:-/var/iri/code}"
STATE_DIR="${IRI_STATE_DIR:-/var/lib/iri}"
BACKUP_DIR="${IRI_BACKUP_DIR:-/var/iri/backup}"
STAGING_BASE="${BACKUP_DIR}/staging"
LOCKFILE="${IRI_LOCKFILE:-/var/lock/iri-deploy.lock}"
BACKUP_ENV="${IRI_BACKUP_ENV:-/etc/iri/backup.env}"
NPM_DIR="${IRI_NPM_DIR:-/var/iri/npm}"
PROFILE=prod

# Writer services quiesced for the dump. Keycloak + the two Postgres DBs + Redis
# + NPM stay up (NPM must, to serve the maintenance page). Space-separated; word
# splitting is intentional for these fixed names.
WRITER_SERVICES="frontend backend ingest"
STOP_TIMEOUT="${IRI_BACKUP_STOP_TIMEOUT:-30}"
LOCK_WAIT="${IRI_BACKUP_LOCK_WAIT:-300}"

# GFS retention (REQ-OPS-008). Overridable from backup.env.
KEEP_DAILY="${IRI_KEEP_DAILY:-7}"
KEEP_WEEKLY="${IRI_KEEP_WEEKLY:-4}"
KEEP_MONTHLY="${IRI_KEEP_MONTHLY:-6}"

# Image used for the throwaway helper that tars the root-owned NPM bind mount
# (the deploy user cannot read it directly; a container running as root can).
# Defaults to the Postgres image, which is always present on the host.
HELPER_IMAGE="${IRI_BACKUP_HELPER_IMAGE:-postgres:18-alpine}"

QUIESCE=true
SKIP_UPLOAD=false
DRY_RUN=false

# --- CLI args ---------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-quiesce) QUIESCE=false; shift ;;
    --skip-upload) SKIP_UPLOAD=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help)
      cat <<'USAGE'
Usage: backup.sh [--no-quiesce] [--skip-upload] [--dry-run]

Captures a consistent full-restore backup set and pushes it client-side
encrypted to Nextcloud via restic. See the header of this file and
docs/backup.md for the operator runbook.

Options:
  --no-quiesce   Do NOT stop the writer services; rely on pg_dump's own MVCC
                 snapshot consistency. Zero downtime, slightly weaker cross-DB
                 guarantee (benign for this app).
  --skip-upload  Capture the dumps to staging but do not run restic (debugging).
  --dry-run      Resolve config and print the plan + `restic snapshots`; stop,
                 dump and upload nothing.
  -h, --help     Show this help.

Configuration (in /etc/iri/backup.env, sourced at start):
  RESTIC_REPOSITORY   e.g. rclone:nextcloud:Basetool-Backups   (required)
  RESTIC_PASSWORD     restic repo encryption password           (or RESTIC_PASSWORD_FILE)
  RCLONE_CONFIG       path to rclone.conf with the `nextcloud` webdav remote
  IRI_KEEP_DAILY / IRI_KEEP_WEEKLY / IRI_KEEP_MONTHLY  (GFS retention; 7/4/6)
USAGE
      exit 0 ;;
    *) echo "FATAL: unknown argument: $1 (try --help)" >&2; exit 1 ;;
  esac
done

# --- Helpers ----------------------------------------------------------------
log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "FATAL: $*"; exit 1; }

# Read a single KEY=value from the host .env (same approach as deploy.sh). Never
# echoes the value to logs.
read_env() {
  grep -E "^$1=" "${COMPOSE_DIR}/.env" 2>/dev/null | tail -n1 | cut -d= -f2- | tr -d '"' || true
}

# docker compose wrapper pinned to the prod profile, run from the compose dir.
dc() { docker compose --profile "${PROFILE}" "$@"; }

# --- Pre-flight -------------------------------------------------------------
[[ -f "${COMPOSE_DIR}/docker-compose.yml" ]] || fail "missing ${COMPOSE_DIR}/docker-compose.yml"
[[ -f "${COMPOSE_DIR}/.env" ]] || fail "missing ${COMPOSE_DIR}/.env"
[[ -f "${BACKUP_ENV}" ]] || fail "missing ${BACKUP_ENV} (restic repo + rclone config; see docs/backup.md)"
command -v docker >/dev/null 2>&1 || fail "docker not found"
command -v restic >/dev/null 2>&1 || fail "restic not found (apt install restic)"
command -v rclone >/dev/null 2>&1 || fail "rclone not found (apt install rclone)"

# The deploy user has no usable $HOME; pin the tool config/cache dirs into
# STATE_DIR (already in the systemd unit's ReadWritePaths) so docker/restic do
# not try to write under an unreachable home.
export DOCKER_CONFIG="${DOCKER_CONFIG:-${STATE_DIR}/.docker}"
export RESTIC_CACHE_DIR="${RESTIC_CACHE_DIR:-${STATE_DIR}/restic-cache}"
mkdir -p "${DOCKER_CONFIG}" "${RESTIC_CACHE_DIR}" "${STAGING_BASE}"

# Load the backup secrets/config (RESTIC_REPOSITORY, RESTIC_PASSWORD, RCLONE_CONFIG, …).
# shellcheck disable=SC1090
set -a; . "${BACKUP_ENV}"; set +a
[[ -n "${RESTIC_REPOSITORY:-}" ]] || fail "RESTIC_REPOSITORY not set in ${BACKUP_ENV}"
[[ -n "${RESTIC_PASSWORD:-}${RESTIC_PASSWORD_FILE:-}" ]] || fail "RESTIC_PASSWORD or RESTIC_PASSWORD_FILE not set in ${BACKUP_ENV}"

KEYSTORE_PATH="$(read_env IRI_KEYSTORE_HOST_PATH)"
KEYSTORE_PATH="${KEYSTORE_PATH:-/var/iri/secrets/keystore.p12}"

cd "${COMPOSE_DIR}"

if docker compose version --short >/dev/null 2>&1; then :; else fail "docker compose v2 not available"; fi

# --- Dry run ----------------------------------------------------------------
if [[ "${DRY_RUN}" == "true" ]]; then
  log "DRY RUN — would back up: krt_basetool + keycloak dumps, ${NPM_DIR}, .env, ${KEYSTORE_PATH}, realm-export.json, keycloak/providers"
  log "DRY RUN — quiesce=${QUIESCE} (stop: ${WRITER_SERVICES}); repo=${RESTIC_REPOSITORY}; retention ${KEEP_DAILY}/${KEEP_WEEKLY}/${KEEP_MONTHLY}"
  log "existing snapshots:"
  restic snapshots --compact 2>&1 | sed 's/^/  /' || log "  (repo not reachable / not initialized yet)"
  exit 0
fi

# --- Lock (shared with deploy.sh) -------------------------------------------
exec 200>"${LOCKFILE}"
if ! flock -w "${LOCK_WAIT}" 200; then
  fail "could not acquire deploy lock within ${LOCK_WAIT}s (a deploy may be running) — skipping this backup"
fi

# --- Staging + restart safety net -------------------------------------------
TS="$(date -u +%Y%m%dT%H%M%SZ)"
STAGING="${STAGING_BASE}/${TS}"
mkdir -p "${STAGING}/config"
chmod 700 "${STAGING}"

QUIESCED=false
cleanup() {
  local rc=$?
  # Safety net: if we stopped the writers and never restarted them (a dump
  # failed), bring them back so production is not left down.
  if [[ "${QUIESCED}" == "true" ]]; then
    log "cleanup: writers still stopped — restarting ${WRITER_SERVICES}"
    dc start ${WRITER_SERVICES} >/dev/null 2>&1 || log "WARN: failed to restart writers during cleanup"
    QUIESCED=false
  fi
  # The staged dumps contain plaintext secrets + PII — never leave them around.
  [[ -n "${STAGING:-}" && -d "${STAGING}" ]] && rm -rf "${STAGING}" || true
  exit "${rc}"
}
trap cleanup EXIT

# --- Quiesce writers (REQ-OPS-009) ------------------------------------------
if [[ "${QUIESCE}" == "true" ]]; then
  log "quiescing writers for the dump: stop ${WRITER_SERVICES} (NPM serves the maintenance page)"
  dc stop -t "${STOP_TIMEOUT}" ${WRITER_SERVICES}
  QUIESCED=true
else
  log "running ONLINE (no quiesce): relying on pg_dump MVCC snapshot consistency"
fi

# --- Dump the databases (creds stay inside the containers) ------------------
log "dumping backend database (krt_basetool)"
dc exec -T db-backend sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -h 127.0.0.1 -p 15432 -Fc "$POSTGRES_DB"' \
  > "${STAGING}/krt_basetool.dump"

log "dumping Keycloak database (keycloak)"
dc exec -T db-keycloak sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -h 127.0.0.1 -p 15433 -Fc "$POSTGRES_DB"' \
  > "${STAGING}/keycloak.dump"

# --- Capture NPM state (root-owned bind mount → via a root container) -------
log "capturing nginx-proxy-manager state (${NPM_DIR})"
docker run --rm -v "${NPM_DIR}:/src:ro" "${HELPER_IMAGE}" tar -C /src -cz . \
  > "${STAGING}/npm.tar.gz"

# --- Capture host secrets / config needed for a full restore ----------------
log "capturing host config (.env, keystore, realm-export, providers)"
cp -p "${COMPOSE_DIR}/.env" "${STAGING}/config/dotenv"
[[ -f "${KEYSTORE_PATH}" ]] && cp -p "${KEYSTORE_PATH}" "${STAGING}/config/keystore.p12" \
  || log "WARN: keystore not found at ${KEYSTORE_PATH} — skipped"
[[ -f "${COMPOSE_DIR}/realm-export.json" ]] && cp -p "${COMPOSE_DIR}/realm-export.json" "${STAGING}/config/realm-export.json" || true
if [[ -d "${COMPOSE_DIR}/keycloak/providers" ]]; then
  tar -C "${COMPOSE_DIR}/keycloak" -czf "${STAGING}/config/providers.tar.gz" providers 2>/dev/null \
    || log "WARN: could not archive keycloak/providers — skipped"
fi

# --- Restart writers BEFORE the slow upload, then release the deploy lock ----
if [[ "${QUIESCED}" == "true" ]]; then
  log "dumps captured — restarting writers (${WRITER_SERVICES})"
  dc start ${WRITER_SERVICES}
  QUIESCED=false
fi
flock -u 200 || true
log "deploy lock released; the rest runs while fully live"

if [[ "${SKIP_UPLOAD}" == "true" ]]; then
  log "--skip-upload: dumps staged at ${STAGING} (will be removed on exit); not pushing to restic"
  exit 0
fi

# --- Push to Nextcloud via restic (client-side encrypted) -------------------
if ! restic snapshots >/dev/null 2>&1; then
  log "restic repository not reachable yet — attempting one-time init"
  restic init || fail "restic init failed — check ${BACKUP_ENV} (repo URL, password, rclone remote)"
fi

log "uploading encrypted snapshot to ${RESTIC_REPOSITORY}"
restic backup --tag basetool --host basetool-prod "${STAGING}"

log "applying GFS retention (keep daily=${KEEP_DAILY} weekly=${KEEP_WEEKLY} monthly=${KEEP_MONTHLY}) + prune"
restic forget --tag basetool \
  --keep-daily "${KEEP_DAILY}" --keep-weekly "${KEEP_WEEKLY}" --keep-monthly "${KEEP_MONTHLY}" \
  --prune

log "verifying repository integrity (restic check)"
restic check

log "backup complete"
