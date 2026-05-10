#!/bin/bash
# =============================================================================
# IRIDIUM Basetool - Nightly Update Script
# Fährt alle Prod-Container herunter, pullt neue Images und startet sie neu.
# Cron-Beispiel (täglich um 03:30 Uhr):
#   30 3 * * * /var/iri/code/scripts/nightly-update.sh >> /var/log/iri-nightly-update.log 2>&1
#
# Supply-chain note:
#   `docker compose pull` resolves each external image (postgres, redis,
#   keycloak, nginx-proxy-manager) by tag, NOT by digest. A compromised
#   upstream image with the same tag would be pulled silently. For higher
#   assurance, pin the externally-sourced services in docker-compose.yml to
#   an explicit @sha256:<digest>:
#     image: postgres:18-alpine@sha256:<digest>
#   Refresh the digests deliberately as part of an upgrade workflow rather
#   than letting this nightly job silently move them.
# =============================================================================

set -euo pipefail

COMPOSE_DIR="/var/iri/code"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
PROFILE="prod"
LOCKFILE="/var/lock/iri-nightly-update.lock"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# -----------------------------------------------------------------------------
# Lock: verhindert parallele Ausführung (z. B. bei zu langem vorherigen Lauf)
# -----------------------------------------------------------------------------
exec 200>"${LOCKFILE}"
flock -n 200 || {
  echo "[${TIMESTAMP}] [FEHLER] Script läuft bereits (Lock: ${LOCKFILE}). Abbruch."
  exit 1
}

echo "=============================================="
echo "[${TIMESTAMP}] Starte nächtliches Update..."
echo "=============================================="

cd "${COMPOSE_DIR}"

echo "[$(date '+%H:%M:%S')] Fahre Container herunter (Profil: ${PROFILE})..."
docker compose --file "${COMPOSE_FILE}" --profile "${PROFILE}" down --timeout 60

echo "[$(date '+%H:%M:%S')] Pulle neue Images (Profil: ${PROFILE})..."
docker compose --file "${COMPOSE_FILE}" --profile "${PROFILE}" pull

echo "[$(date '+%H:%M:%S')] Starte Container neu (Profil: ${PROFILE})..."
docker compose --file "${COMPOSE_FILE}" --profile "${PROFILE}" up -d

# -----------------------------------------------------------------------------
# Health-Check: prüft nach dem Start ob alle Container healthy sind
# -----------------------------------------------------------------------------
echo "[$(date '+%H:%M:%S')] Warte 60 Sekunden auf Container-Startup..."
sleep 60

echo "[$(date '+%H:%M:%S')] Prüfe Container-Health..."
UNHEALTHY=$(docker compose --file "${COMPOSE_FILE}" --profile "${PROFILE}" ps \
  --format "{{.Name}}: {{.Health}}" 2>/dev/null \
  | grep -v ": healthy" | grep -v ": $" || true)

if [ -n "${UNHEALTHY}" ]; then
  echo "[$(date '+%H:%M:%S')] [FEHLER] Folgende Container sind nicht healthy:"
  echo "${UNHEALTHY}"
  echo "[$(date '+%H:%M:%S')] Nächtliches Update abgeschlossen MIT FEHLER."
  # Non-zero exit so cron / monitoring picks this up instead of treating a
  # silently broken stack as a normal nightly run.
  exit 1
fi

echo "[$(date '+%H:%M:%S')] Alle Container sind healthy."
echo "[$(date '+%H:%M:%S')] Nächtliches Update abgeschlossen."
