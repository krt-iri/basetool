#!/bin/bash
# =============================================================================
# Profit Basetool — weekly restore drill (recoverability proof, REQ-OPS-011)
#
# A backup you have never restored is a hope, not a backup. This script pulls
# the LATEST restic snapshot from Nextcloud, restores both database dumps into a
# THROWAWAY PostgreSQL container, and verifies them with sanity queries. It
# touches NOTHING in production — its own ephemeral container only. On any
# failure it exits non-zero so iri-restore-drill.service shows `failed`,
# journald flags it, and any OnFailure= hook fires.
#
# Runs weekly from iri-restore-drill.timer, or manually:
#   sudo -u deploy /var/iri/code/scripts/restore-drill.sh
#   sudo -u deploy /var/iri/code/scripts/restore-drill.sh --keep   # don't tear down on success (debug)
#
# Verification (a restore that produced an empty/garbage DB must FAIL):
#   * backend  — flyway_schema_history exists AND has rows (migrations restored)
#   * backend  — public-schema table count above a floor
#   * keycloak — table count above a floor
# =============================================================================

set -euo pipefail

STATE_DIR="${IRI_STATE_DIR:-/var/lib/iri}"
BACKUP_DIR="${IRI_BACKUP_DIR:-/var/iri/backup}"
WORK_BASE="${BACKUP_DIR}/restore-drill"
BACKUP_ENV="${IRI_BACKUP_ENV:-/etc/iri/backup.env}"
DRILL_IMAGE="${IRI_DRILL_IMAGE:-postgres:18-alpine}"
CONTAINER="iri-restore-drill"
READY_TIMEOUT="${IRI_DRILL_READY_TIMEOUT:-60}"
MIN_BACKEND_TABLES="${IRI_DRILL_MIN_BACKEND_TABLES:-20}"
MIN_KEYCLOAK_TABLES="${IRI_DRILL_MIN_KEYCLOAK_TABLES:-20}"

KEEP=false
[[ "${1:-}" == "--keep" ]] && KEEP=true

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "FATAL: $*"; exit 1; }

# --- Pre-flight -------------------------------------------------------------
[[ -f "${BACKUP_ENV}" ]] || fail "missing ${BACKUP_ENV}"
command -v docker >/dev/null 2>&1 || fail "docker not found"
command -v restic >/dev/null 2>&1 || fail "restic not found"
command -v rclone >/dev/null 2>&1 || fail "rclone not found"

export DOCKER_CONFIG="${DOCKER_CONFIG:-${STATE_DIR}/.docker}"
export RESTIC_CACHE_DIR="${RESTIC_CACHE_DIR:-${STATE_DIR}/restic-cache}"
mkdir -p "${DOCKER_CONFIG}" "${RESTIC_CACHE_DIR}" "${WORK_BASE}"

# shellcheck disable=SC1090
set -a; . "${BACKUP_ENV}"; set +a
[[ -n "${RESTIC_REPOSITORY:-}" ]] || fail "RESTIC_REPOSITORY not set in ${BACKUP_ENV}"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="${WORK_BASE}/${TS}"
mkdir -p "${WORK}"
chmod 700 "${WORK}"

cleanup() {
  local rc=$?
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  if [[ "${KEEP}" == "true" && "${rc}" -eq 0 ]]; then
    log "--keep: leaving restored dumps at ${WORK}"
  else
    rm -rf "${WORK}" 2>/dev/null || true
  fi
  exit "${rc}"
}
trap cleanup EXIT

# --- Restore the latest snapshot's dumps ------------------------------------
log "restoring latest snapshot dumps from ${RESTIC_REPOSITORY}"
restic restore latest --tag basetool \
  --include '*/krt_basetool.dump' --include '*/keycloak.dump' \
  --target "${WORK}" \
  || fail "restic restore failed"

BACKEND_DUMP="$(find "${WORK}" -name krt_basetool.dump -print -quit)"
KEYCLOAK_DUMP="$(find "${WORK}" -name keycloak.dump -print -quit)"
[[ -s "${BACKEND_DUMP:-}" ]] || fail "backend dump not found in restored snapshot"
[[ -s "${KEYCLOAK_DUMP:-}" ]] || fail "keycloak dump not found in restored snapshot"
log "restored: $(du -h "${BACKEND_DUMP}" | cut -f1) backend, $(du -h "${KEYCLOAK_DUMP}" | cut -f1) keycloak"

# --- Spin a throwaway Postgres + restore into it ----------------------------
docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
log "starting throwaway Postgres (${DRILL_IMAGE})"
docker run -d --name "${CONTAINER}" \
  -e POSTGRES_USER=drill -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=postgres \
  "${DRILL_IMAGE}" >/dev/null

log "waiting for it to become ready (timeout ${READY_TIMEOUT}s)"
deadline=$(( $(date +%s) + READY_TIMEOUT ))
until docker exec "${CONTAINER}" pg_isready -U drill -d postgres >/dev/null 2>&1; do
  (( $(date +%s) < deadline )) || fail "throwaway Postgres did not become ready"
  sleep 2
done

dexec() { docker exec -i "${CONTAINER}" "$@"; }

log "restoring backend dump → krt_basetool"
dexec createdb -U drill krt_basetool
docker cp "${BACKEND_DUMP}" "${CONTAINER}:/tmp/krt_basetool.dump"
dexec pg_restore -U drill -d krt_basetool --no-owner --no-privileges /tmp/krt_basetool.dump \
  || log "WARN: pg_restore (backend) reported non-fatal errors — verifying anyway"

log "restoring keycloak dump → keycloak"
dexec createdb -U drill keycloak
docker cp "${KEYCLOAK_DUMP}" "${CONTAINER}:/tmp/keycloak.dump"
dexec pg_restore -U drill -d keycloak --no-owner --no-privileges /tmp/keycloak.dump \
  || log "WARN: pg_restore (keycloak) reported non-fatal errors — verifying anyway"

# --- Verify (the actual proof) ----------------------------------------------
q() { dexec psql -U drill -d "$1" -tAc "$2" | tr -d '[:space:]'; }

FLYWAY_ROWS="$(q krt_basetool "select count(*) from flyway_schema_history" 2>/dev/null || echo 0)"
BACKEND_TABLES="$(q krt_basetool "select count(*) from information_schema.tables where table_schema='public'" 2>/dev/null || echo 0)"
KEYCLOAK_TABLES="$(q keycloak "select count(*) from information_schema.tables where table_schema='public'" 2>/dev/null || echo 0)"

log "verification: flyway_schema_history rows=${FLYWAY_ROWS}, backend public tables=${BACKEND_TABLES}, keycloak public tables=${KEYCLOAK_TABLES}"

ok=true
[[ "${FLYWAY_ROWS}" =~ ^[0-9]+$ && "${FLYWAY_ROWS}" -gt 0 ]] || { log "FAIL: flyway_schema_history empty or missing"; ok=false; }
[[ "${BACKEND_TABLES}" =~ ^[0-9]+$ && "${BACKEND_TABLES}" -ge "${MIN_BACKEND_TABLES}" ]] || { log "FAIL: backend public tables < ${MIN_BACKEND_TABLES}"; ok=false; }
[[ "${KEYCLOAK_TABLES}" =~ ^[0-9]+$ && "${KEYCLOAK_TABLES}" -ge "${MIN_KEYCLOAK_TABLES}" ]] || { log "FAIL: keycloak public tables < ${MIN_KEYCLOAK_TABLES}"; ok=false; }

if [[ "${ok}" == "true" ]]; then
  log "RESTORE DRILL PASSED — the off-site backup is recoverable"
  exit 0
fi
fail "RESTORE DRILL FAILED — the latest backup did not restore cleanly (investigate immediately)"
