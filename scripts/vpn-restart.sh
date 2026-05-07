#!/bin/bash
# =============================================================================
# IRIDIUM Basetool – VPN Restart Script
# Startet die WireGuard-Schnittstelle wg0 neu (down + up).
# Cron-Beispiel (täglich um 04:00 Uhr):
#   0 4 * * * /var/iri/code/scripts/vpn-restart.sh >> /var/log/iri-vpn-restart.log 2>&1
# =============================================================================

set -euo pipefail

INTERFACE="wg0"
LOCKFILE="/var/lock/iri-vpn-restart.lock"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# -----------------------------------------------------------------------------
# Lock: verhindert parallele Ausführung
# -----------------------------------------------------------------------------
exec 200>"${LOCKFILE}"
flock -n 200 || {
  echo "[${TIMESTAMP}] [FEHLER] Script läuft bereits (Lock: ${LOCKFILE}). Abbruch."
  exit 1
}

echo "=============================================="
echo "[${TIMESTAMP}] Starte VPN-Neustart (${INTERFACE})..."
echo "=============================================="

echo "[$(date '+%H:%M:%S')] Fahre WireGuard-Schnittstelle ${INTERFACE} herunter..."
wg-quick down "${INTERFACE}"

echo "[$(date '+%H:%M:%S')] Starte WireGuard-Schnittstelle ${INTERFACE} neu..."
wg-quick up "${INTERFACE}"

echo "[$(date '+%H:%M:%S')] VPN-Neustart abgeschlossen."
