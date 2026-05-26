# Domain-Wechsel: `iri-base.org` → `profit-base.online`

Schritt-für-Schritt-Runbook für den Operator. Die Code-Änderungen (Tool-Name "Profit Basetool", neuer Hostname `profit-base.online` in allen YML / Java / Tests / Doku, neues Header-Logo `krt.webp`) liegen als **PR #165** vor: <https://github.com/krt-iri/basetool/pull/165> — mehrfach gegen `main` rebased, alle Tests grün. Diese Anleitung deckt nur die **Operator-Aktionen auf dem Production-Host** ab, die der Code-PR allein nicht bewältigt. Voraussetzung: der PR ist auf `main` gemerged, sonst sind die neuen `KC_HOSTNAME`-/`KEYCLOAK_ISSUER_URI`-Defaults nicht im Image.

> **Geschätztes Wartungsfenster:** 30–60 Minuten Down-Time für die User (zwischen "Stack stop" und "Smoke-Test grün"). Vorbereitung (Phase 0–2) kann mehrere Stunden im Voraus laufen, ohne Service-Impact.

> **Auswirkung auf User-Sessions:** Der Wechsel des `KC_HOSTNAME` ändert den JWT-`iss`-Claim. **Alle laufenden Sessions werden ungültig** — jeder User muss sich nach dem Wechsel neu einloggen. Bitte vorher per Discord / E-Mail / Announcement-Banner ankündigen.

---

## Voraussetzungen

- SSH-Zugriff auf den Production-Host als ein User mit `sudo`-Rechten.
- Login-Zugang zur Domain-Registrar-Web-Oberfläche (für DNS-Edits).
- Login zum NPM-Admin: `https://<host>:10081`.
- Login zur Keycloak-Admin-Konsole: `https://keycloak.iri-base.org/admin/` (während der Migration noch erreichbar; nach Phase 6 dann `https://keycloak.profit-base.online/admin/`).
- GitHub-Repo-Push-Rechte + `gh`-CLI lokal eingerichtet (für `promote.yml`).
- Eine getestete Backup-Strategie für `/var/iri/code/.env`, `/var/iri/code/realm-export.json`, `/var/iri/db-backend`, `/var/iri/db-keycloak`.

---

## Phase 0 — Vorbereitung (kein Service-Impact)

### 0.1 DNS-Records anlegen

Beim Domain-Registrar (oder DNS-Provider) für `profit-base.online`:

| Record | Type | Wert | TTL |
| --- | --- | --- | --- |
| `profit-base.online` | `A` | `<IP des Prod-Hosts>` | `300` (5 min) |
| `profit-base.online` | `AAAA` | `<IPv6 des Prod-Hosts>`, falls vorhanden | `300` |
| `keycloak.profit-base.online` | `A` | `<IP des Prod-Hosts>` | `300` |
| `keycloak.profit-base.online` | `AAAA` | `<IPv6>`, falls vorhanden | `300` |
| `www.profit-base.online` (optional) | `CNAME` | `profit-base.online` | `300` |

Niedrige TTL ist wichtig, damit Korrekturen im Fehlerfall schnell wirken. Nach Stabilisierung in Phase 9 wieder auf einen sinnvollen Wert (≥ 3600s) anheben.

**Verifikation** (von einem externen Rechner, nicht vom Prod-Host):

```bash
dig +short profit-base.online
dig +short keycloak.profit-base.online
# beide müssen die Prod-Host-IP zurückgeben
```

DNS-Propagation kann 5 Minuten bis mehrere Stunden dauern, je nach Caching-Layer. Phase 1+ erst starten, wenn beide `dig`-Calls die richtige IP liefern.

### 0.2 Backup erstellen

Auf dem Prod-Host (als `deploy`-User oder via `sudo`):

```bash
# Backup-Verzeichnis anlegen (idempotent, existiert beim allerersten Mal noch nicht)
sudo install -d -m 0750 -o deploy -g docker /var/iri/backups

# DB-Credentials aus der .env in die Shell laden — sonst sind ${POSTGRES_USER:?}
# und ${KC_POSTGRES_USER:?} im pg_dump-Aufruf nicht gesetzt und der Dump
# scheitert mit "unbound variable". `set -a` exportiert jede Variable
# automatisch, `set +a` schaltet das wieder ab.
set -a; . /var/iri/code/.env; set +a

# Konfigurationsdateien
sudo cp /var/iri/code/.env /var/iri/code/.env.backup-$(date +%Y%m%d-%H%M)
sudo cp /var/iri/code/realm-export.json /var/iri/code/realm-export.json.backup-$(date +%Y%m%d-%H%M)
sudo cp /var/iri/code/docker-compose.yml /var/iri/code/docker-compose.yml.backup-$(date +%Y%m%d-%H%M)

# Datenbanken (Postgres-Dumps)
sudo -u deploy docker compose --profile prod exec -T db-backend \
    pg_dump -U "${POSTGRES_USER:?}" -d "${POSTGRES_DB:?}" \
    > /var/iri/backups/db-backend-$(date +%Y%m%d-%H%M).sql
sudo -u deploy docker compose --profile prod exec -T db-keycloak \
    pg_dump -U "${KC_POSTGRES_USER:?}" -d "${KC_POSTGRES_DB:?}" \
    > /var/iri/backups/db-keycloak-$(date +%Y%m%d-%H%M).sql

# Aktuell deployed-version notieren (für Rollback)
cat /var/lib/iri/last-deployed.digests > /var/iri/backups/last-deployed-$(date +%Y%m%d-%H%M).digests
cat /var/lib/iri/current-digest-pin.yml > /var/iri/backups/current-pin-$(date +%Y%m%d-%H%M).yml

# NPM-State (SQLite + Let's-Encrypt-Certs) — falls beim Anlegen der neuen
# Proxy-Hosts in Phase 2 etwas schiefgeht, ist hier der Rollback-Anker.
sudo tar -czf /var/iri/backups/npm-state-$(date +%Y%m%d-%H%M).tgz \
    -C /var/iri npm
```

### 0.3 Aktuelle Image-Version notieren (Rollback-Anker)

```bash
sudo -u deploy docker compose --profile prod images backend frontend
# Notiere die `IMAGE TAG`-Spalte — typisch `:stable`, aber der wirkliche
# Rollback-Punkt ist der Digest in /var/iri/backups/last-deployed-*.digests
```

---

## Phase 1 — Release der Code-Änderungen (kein Service-Impact)

Diese Phase produziert die neuen Container-Images mit dem `Profit Basetool`-Branding, dem neuen `krt.webp`-Logo, und der neuen URL in den Default-Configs.

### 1.1 PR #165 mergen

PR liegt fertig: <https://github.com/krt-iri/basetool/pull/165>. Der Branch
`claude/trusting-archimedes-c2a20e` ist mehrfach gegen `main` rebased,
alle CI-Checks (Tests, Spotless, Checkstyle, SpotBugs, DCO) grün. Der PR enthält
zwei Commits (Rebranding-Code + Runbook-Doku-Update) — **Squash-Merge** wird
empfohlen, weil das die ganze Umstellung als einen sauberen Commit auf `main`
landen lässt. Sobald auf `main`, baut `release-images.yml` automatisch.

**Empfohlen — versionierten Tag direkt nach dem Merge setzen**, damit Phase 5
einen sauberen `:1.5.0`-Rollback-Anker hat. Ohne Tag werden nur `:edge` /
`:latest` / `:sha-<short>` als Tags produziert, und der Promote-Workflow muss
dann mit einem dieser Tags getriggert werden — funktioniert auch, ist aber
weniger explizit:

```bash
git fetch origin main
git tag -a v1.5.0 main -m "Rename to Profit Basetool + URL switch to profit-base.online"
git push origin v1.5.0
```

Falls kein Tag gesetzt wurde, läuft Phase 5 mit `version=latest` (Tag-Push) oder
`version=sha-<7chars-des-merge-commits>` (Main-Push) — der konkrete Tag-Name
steht in `release-images.yml`'s Job-Log unter "Build & push image" ganz oben.

### 1.2 Release-Pipeline durchlaufen lassen

Im GitHub-UI unter Actions → "release-images.yml" verifizieren, dass beide Module für beide Architekturen gebaut und nach GHCR gepusht wurden:

```
ghcr.io/krt-iri/basetool-backend:1.5.0   (linux/amd64 + linux/arm64 als Multi-Arch-Index)
ghcr.io/krt-iri/basetool-frontend:1.5.0
```

Seit der Pipeline-Migration in #162 / #163 läuft ARM64 nativ auf GitHubs `ubuntu-24.04-arm`-Fleet (vorher unter QEMU), beide Architekturen finishen in ähnlicher Wallclock-Zeit. Dauer typisch 8–15 Minuten (4 parallele Build-Jobs + 2 Merge-Jobs).

### 1.3 Stable-Promote NOCH NICHT triggern

`:stable` zeigt zu diesem Zeitpunkt noch auf die alte Version. Der nächste `iri-deploy.timer`-Tick würde also nicht versehentlich die neue Version mit den neuen URLs ausrollen, bevor DNS / TLS / Keycloak vorbereitet sind. Promote machen wir in Phase 5.

---

## Phase 2 — NPM-Proxy-Hosts vorbereiten (kein Service-Impact)

Während die alte `iri-base.org`-Domain noch produktiv ist, legen wir die neuen Proxy-Hosts parallel an.

### 2.1 NPM-Admin öffnen

```
https://<host>:10081
```

### 2.2 Proxy-Host für Frontend anlegen

→ **Hosts** → **Proxy Hosts** → **Add Proxy Host**

- **Domain Names:** `profit-base.online`, `www.profit-base.online`
- **Scheme:** `http`
- **Forward Hostname:** `frontend`
- **Forward Port:** `18081`
- **Cache Assets:** off
- **Block Common Exploits:** on
- **Websockets Support:** on
- **Access List:** Public (oder bestehende Allowlist)

→ **SSL** Tab:
- **SSL Certificate:** Request a new SSL Certificate (Let's Encrypt)
- **Force SSL:** on
- **HTTP/2 Support:** on
- **HSTS Enabled:** on
- **HSTS Subdomains:** on
- **Email Address for Let's Encrypt:** (deine Op-Email)
- **I Agree to the Let's Encrypt TOS:** on
- → **Save**

NPM stellt das Cert via HTTP-01-Challenge aus (dauert 30–60 Sekunden). Falls Fehler: prüfe, ob DNS bereits propagiert ist (`dig`) und ob NPM auf Port 80 erreichbar ist.

### 2.3 Proxy-Host für Keycloak anlegen

Gleiche Schritte mit:
- **Domain Names:** `keycloak.profit-base.online`
- **Forward Hostname:** `keycloak`
- **Forward Port:** `18080`
- **Scheme:** `http`
- Rest wie 2.2.

### 2.4 Vorab-Verifikation

```bash
# Cert sollte valide sein (auch wenn der Upstream noch alte Domain config hat):
curl -vI https://profit-base.online/ 2>&1 | grep -E "(SSL|HTTP/)"
# Erwartet: 200 oder 302 (Login-Redirect), Cert validates
curl -vI https://keycloak.profit-base.online/realms/iri/.well-known/openid-configuration 2>&1 | head -20
# Erwartet: 200 + JSON content-type. Solange Keycloak noch
# KC_HOSTNAME=keycloak.iri-base.org läuft, antwortet Keycloak hier mit
# einem Redirect zur alten Domain oder einem 421/404 — das ist OK,
# wir prüfen nur das Cert + DNS-Routing.
```

---

## Phase 3 — Keycloak-Client-Config vorbereiten (kein Service-Impact)

Wir ergänzen die neuen Domain-Einträge im laufenden Keycloak, **ohne** die alten zu entfernen. So bleiben beide Domains während des Wechsels gültig — falls etwas schiefgeht, kann der OAuth2-Flow nahtlos auf der alten Domain weiterlaufen.

### 3.1 In der Keycloak-Admin einloggen

```
https://keycloak.iri-base.org/admin/
```

User: `KC_BOOTSTRAP_ADMIN_USERNAME` aus der `.env`.

### 3.2 Realm `iri` → Clients → `basetool-frontend`

**Valid Redirect URIs** — neue Einträge **dazu** (alte behalten):

```
https://profit-base.online/login/oauth2/code/keycloak
https://profit-base.online/*
```

**Valid Post Logout Redirect URIs** — neue Einträge **dazu**:

```
https://profit-base.online/*
https://profit-base.online
```

**Web Origins** — neuen Eintrag **dazu**:

```
https://profit-base.online
```

→ **Save**

Test im Inkognito-Browser, noch auf der alten Domain:
- `https://iri-base.org/` → Login-Button → Login-Flow muss weiterhin funktionieren (die alten Redirect-URIs sind ja noch da).

### 3.3 Realm-Export herunterladen (Persistenz)

Damit ein Reimport (oder ein frischer Stack-Spin-up) die neue Config nicht verliert, exportieren wir den Realm-Stand:

→ **Realm settings** → **Action** (oben rechts) → **Partial export**
- **Include groups and roles:** off (nicht nötig)
- **Include clients:** on
- → **Export**

Die heruntergeladene JSON-Datei enthält **nur** den Client-Diff — das `realm-export.json` auf dem Host ist Full-Export. Beste Strategie: Manuelles Merging.

Alternative (sicherer): Im Admin-UI **Realm settings** → **Action** → **Partial export** zeigt nur den selektierten Teil. Oder via `kc.sh` direkt im Container:

```bash
sudo -u deploy docker compose --profile prod exec keycloak \
    /opt/keycloak/bin/kc.sh export \
    --realm iri \
    --file /tmp/realm-export-new.json
sudo -u deploy docker compose --profile prod cp \
    keycloak:/tmp/realm-export-new.json /tmp/realm-export-new.json
```

Die Datei `/tmp/realm-export-new.json` wird in Phase 6.2 das alte `/var/iri/code/realm-export.json` ersetzen.

---

## Phase 4 — Wartungsfenster beginnt: Stack-Stop

Ab hier beginnt die Down-Time für User. Vorher Announcement raus.

### 4.1 Auto-Deploy-Timer stoppen

Damit kein versehentlicher `:stable`-Promote oder Image-Update den manuellen Workflow stört:

```bash
sudo systemctl stop iri-deploy.timer
sudo systemctl status iri-deploy.timer  # bestätigen: inactive (dead)
```

### 4.2 Frontend und Backend stoppen

NPM und Keycloak bleiben weiterhin laufen — die Maintenance-Page erscheint dann automatisch beim ersten User-Request auf eine der beiden Domains (NPM antwortet mit `502/503` vom toten Upstream und der `server_proxy.conf`-Hook switcht auf `maintenance.html`).

```bash
cd /var/iri/code
sudo -u deploy docker compose --profile prod stop frontend backend
sudo -u deploy docker compose --profile prod ps
# Erwartet: frontend + backend exited; keycloak, db-backend, db-keycloak,
# redis, npm bleiben "running (healthy)"
```

**Verifikation Maintenance-Page** (für jeden Browser sichtbar, der jetzt auf die Site geht):

```bash
curl -i https://iri-base.org/ 2>&1 | head -5
# Erwartet: HTTP/1.1 503 + maintenance.html
curl -i https://profit-base.online/ 2>&1 | head -5
# Erwartet: HTTP/1.1 503 + maintenance.html (NPM antwortet auf beide Domains)
```

---

## Phase 5 — Code-Release zu :stable promoten

Lokal (Developer-Rechner):

```bash
gh workflow run promote.yml -f version=1.5.0
```

Oder im GitHub-UI: **Actions** → **Promote to stable** → **Run workflow** → Version `1.5.0`. Dauer ~30 Sekunden.

**Verifikation:**

```bash
# Auf dem Prod-Host: schaut, ob :stable jetzt auf den 1.5.0-Digest zeigt
sudo -u deploy docker manifest inspect ghcr.io/krt-iri/basetool-backend:stable | head -5
# Vergleiche mit :1.5.0:
sudo -u deploy docker manifest inspect ghcr.io/krt-iri/basetool-backend:1.5.0 | head -5
# Beide müssen denselben Digest zeigen.
```

---

## Phase 6 — Konfiguration auf dem Host aktualisieren

### 6.1 `docker-compose.yml` aktualisieren

Die Code-Änderungen enthalten `KC_HOSTNAME: keycloak.profit-base.online` und beide `KEYCLOAK_ISSUER_URI: https://keycloak.profit-base.online/realms/iri`. Diese Compose-Datei lebt im Repo *und* auf dem Host — sie wird **nicht** vom Container-Image mitgebracht und muss separat synchronisiert werden. Zwei Wege, je nach Setup:

**Variante A — scp vom Dev-Rechner** (wenn kein Repo-Clone auf dem Host liegt):

```bash
# Lokal, im Repo-Clone auf main (nach PR-Merge):
git fetch origin main && git checkout main && git pull --ff-only
scp docker-compose.yml deploy@<prod-host>:/tmp/docker-compose.yml
scp -r docker/maintenance/ deploy@<prod-host>:/tmp/docker-maintenance/

# Auf dem Prod-Host:
sudo install -m 0640 -o deploy -g docker /tmp/docker-compose.yml /var/iri/code/docker-compose.yml
```

**Variante B — git pull in einem Operator-Clone auf dem Host** (wenn `/opt/krt-iri/basetool` o.ä. existiert):

```bash
# Auf dem Prod-Host:
cd /opt/krt-iri/basetool                      # oder wo immer der Operator-Clone liegt
sudo -u deploy git fetch origin main && sudo -u deploy git checkout main && sudo -u deploy git pull --ff-only
sudo install -m 0640 -o deploy -g docker docker-compose.yml /var/iri/code/docker-compose.yml
```

**Verifikation** (in beiden Varianten):

```bash
grep -n "profit-base.online" /var/iri/code/docker-compose.yml
# Erwartet: 3 Treffer
#   KC_HOSTNAME: keycloak.profit-base.online
#   KEYCLOAK_ISSUER_URI: https://keycloak.profit-base.online/realms/iri  (backend-template)
#   KEYCLOAK_ISSUER_URI: https://keycloak.profit-base.online/realms/iri  (frontend-template)
```

### 6.2 `realm-export.json` aktualisieren

Die in Phase 3.3 exportierte aktuelle Realm-Config (mit den neuen Redirect-URIs) auf den Host:

```bash
# Datei vom Phase-3.3-Export hochladen, z. B. via scp:
sudo install -m 0640 -o deploy -g docker /tmp/realm-export-new.json /var/iri/code/realm-export.json
```

> **Wichtig:** Das `realm-export.json` wird nur beim **ersten** Keycloak-Start importiert (Realm noch nicht in der `db-keycloak`-DB). Beim Restart in Phase 7 hat Keycloak die Realm-Config bereits in der DB — die Datei wird ignoriert. Sie ist nur relevant, falls die `db-keycloak`-DB jemals neu aufgesetzt werden muss.

### 6.3 Maintenance-Page-Snippets aktualisieren

Die nginx-Maintenance-Hook-Files unter `/var/iri/code/docker/maintenance/` enthalten URL-Referenzen (`maintenance.json` `type`-URI) und Branding-Strings (`maintenance.html`). Die in Phase 6.1 hochgeladenen / gepullten Files reinkopieren:

```bash
# Variante A — wenn via scp hochgeladen (Phase 6.1 Variante A):
sudo install -m 0644 -o deploy -g docker \
    /tmp/docker-maintenance/static/maintenance.html \
    /var/iri/code/docker/maintenance/static/maintenance.html
sudo install -m 0644 -o deploy -g docker \
    /tmp/docker-maintenance/static/maintenance.json \
    /var/iri/code/docker/maintenance/static/maintenance.json

# Variante B — wenn via git pull (Phase 6.1 Variante B):
cd /opt/krt-iri/basetool
sudo install -m 0644 -o deploy -g docker docker/maintenance/static/maintenance.html /var/iri/code/docker/maintenance/static/
sudo install -m 0644 -o deploy -g docker docker/maintenance/static/maintenance.json /var/iri/code/docker/maintenance/static/

# Verifikation:
grep -n "profit-base.online" /var/iri/code/docker/maintenance/static/maintenance.json
# Erwartet: "type": "https://profit-base.online/problems/maintenance"
```

**Reload des nginx-Configs im NPM-Container** — NPM lädt die Hook-Files (`http.conf`, `server_proxy.conf`) nur beim Container-Start neu, der `static/` Mount wird live gelesen. Da wir die Hook-Files in diesem Schritt NICHT anfassen (nur den static-Content), reicht es, NPM zu signalisieren, dass das nginx-Config-Tree neu durchgegangen werden soll:

```bash
sudo -u deploy docker compose --profile prod exec npm nginx -s reload
# Erwartet: keine Ausgabe, exit code 0
```

Falls die Maintenance-Page nach Phase 8.3 trotzdem die alte Version zeigt: `sudo -u deploy docker compose --profile prod restart npm` (full restart, ~5s Down-Time auf dem NPM selbst — bedeutet kurze 502s während Phase 8.3, danach normal).

### 6.4 `.env` prüfen

Per Default enthält `.env` **keine** Hostname-Variable — die URL ist nur in `docker-compose.yml` referenziert. Aber kontrollieren:

```bash
grep -i "iri-base\|profit-base" /var/iri/code/.env
# Erwartet: keine Treffer. Falls doch (custom override): die Zeile von
# iri-base.org auf profit-base.online umstellen.
```

---

## Phase 7 — Stack mit neuen Configs starten

### 7.1 Neue Images pullen

```bash
cd /var/iri/code
sudo -u deploy docker compose --profile prod pull keycloak backend frontend
```

`keycloak` ist hier nur, weil wir den Container-Restart erzwingen wollen (`KC_HOSTNAME` ist eine Env-Var, die nur beim Start gelesen wird). Das Keycloak-Image selbst ändert sich nicht.

### 7.2 Keycloak mit neuem Hostname neustarten

```bash
sudo -u deploy docker compose --profile prod stop keycloak
sudo -u deploy docker compose --profile prod up -d keycloak

# Healthcheck abwarten — Compose-Definition: start_period=30s + retries=15 ×
# interval=10s, also bis zu ~180s bevor der Status auf "unhealthy" kippt.
# Parallel den Live-Log mitlesen (Ctrl-C zum Beenden):
sudo -u deploy docker compose --profile prod logs -f keycloak

# In einem zweiten Terminal periodisch den Status checken:
sudo -u deploy docker compose --profile prod ps keycloak
# Erwartet: keycloak -> running (healthy)
# Wenn nach ~180s noch "starting" / "unhealthy":
sudo -u deploy docker compose --profile prod logs --tail=200 keycloak
# Typischer Fehler: KC_HOSTNAME_STRICT="true" + Hostname-Mismatch → "hostname
#   not resolvable" → DNS-Propagation prüfen (Phase 0.1)
# Anderer typischer Fehler: "Bind for 0.0.0.0:18080 failed: port is already
#   allocated" → der alte Keycloak-Container hängt noch; `docker ps -a` + manuell
#   `docker rm -f <id>`.
```

**Verifikation:**

```bash
curl -s https://keycloak.profit-base.online/realms/iri/.well-known/openid-configuration | head -3
# Erwartet: { "issuer": "https://keycloak.profit-base.online/realms/iri", ... }
# Wichtig: der "issuer" muss exakt die neue URL sein, nicht die alte.
```

### 7.3 Backend und Frontend starten

> **Reihenfolge-Hinweis:** Backend hat im Compose-File `depends_on: keycloak:
> condition: service_healthy`. Wenn Keycloak in 7.2 noch nicht healthy ist,
> hängt der Backend-Start unbestimmt lang. Phase 7.2 also wirklich grün ab-
> warten, bevor 7.3 startet. Im Backend-Log erkennt man das im "waiting for
> keycloak"-Block — wenn der zu lange läuft, hilft `docker compose ps keycloak`
> zur Diagnose.

```bash
sudo -u deploy docker compose --profile prod up -d backend frontend

# Healthcheck abwarten — Backend hat zusätzlich Flyway-Migrations beim
# Startup, die je nach DB-Größe einige Sekunden dauern können. Live-Log:
sudo -u deploy docker compose --profile prod logs -f backend frontend
# In einem zweiten Terminal:
sudo -u deploy docker compose --profile prod ps backend frontend
# Erwartet: beide -> running (healthy)
```

**Verifikation Backend-Banner** (zeigt die neue Tool-Identität):

```bash
sudo -u deploy docker compose --profile prod logs backend | grep -A 8 "Profit Basetool"
# Erwartet:
#  Profit Basetool :: backend ready
#  Active profiles     : [prod]
#  Datasource URL      : jdbc:postgresql://db-backend:15432/krt_basetool
#  Keycloak issuer     : https://keycloak.profit-base.online/realms/iri  ← entscheidend!
```

### 7.4 `last-deployed.digests` aktualisieren

Damit der `iri-deploy.timer` beim nächsten Tick nicht den Stack neu deployt:

```bash
sudo -u deploy /var/iri/code/scripts/deploy.sh --check-only
# Sollte sagen: "No change, already at <digest>"
# Falls "change detected": einmal manuell durchlaufen lassen, damit
# /var/lib/iri/last-deployed.digests die neuen Digests speichert:
sudo -u deploy /var/iri/code/scripts/deploy.sh
```

---

## Phase 8 — Smoke-Test

### 8.1 Basis-Konnektivität

```bash
# Tool über neue Domain:
curl -sI https://profit-base.online/ | head -1
# Erwartet: HTTP/1.1 302 oder 200

# Backend-API (anonymer Endpoint, z. B. ein nicht-existierender Pfad):
curl -s https://profit-base.online/api/v1/missions/00000000-0000-0000-0000-000000000000 | jq .
# Erwartet: RFC 7807 problem+json mit "type": "https://profit-base.online/problems/not-found"
# Wichtig: die "type"-URI muss auf profit-base.online zeigen, nicht iri-base.org.

# Keycloak-Discovery:
curl -s https://keycloak.profit-base.online/realms/iri/.well-known/openid-configuration | jq -r '.issuer, .authorization_endpoint, .token_endpoint'
# Erwartet:
#   https://keycloak.profit-base.online/realms/iri
#   https://keycloak.profit-base.online/realms/iri/protocol/openid-connect/auth
#   https://keycloak.profit-base.online/realms/iri/protocol/openid-connect/token
```

### 8.2 Login-Flow im Inkognito-Browser

> **Warum Inkognito und nicht der normale Browser?** Spring Session speichert
> die OAuth2-Authorized-Client-Daten (inkl. dem alten Issuer-URL) in Redis,
> referenziert über das `SESSION`-Cookie. Ein vor dem Wartungsfenster
> eingeloggter User hat in seinem normalen Browser noch das alte Cookie und
> der Backend-Server hat im Redis noch das alte Token mit `iss: keycloak.iri-base.org`.
> Beim ersten Request nach dem Wechsel kommt ein HTTP 500 oder ein
> Re-Auth-Loop. **Inkognito-Modus startet mit leerem Cookie-Jar** und triggert
> den frischen OAuth2-Flow mit dem neuen Issuer. Alternative: vor Phase 8.2
> die Redis-Session-DB einmal leeren:
>
> ```bash
> sudo -u deploy docker compose --profile prod exec redis \
>     sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning FLUSHDB'
> # Wirkung: jeder eingeloggte User wird einmalig ausgeloggt und muss sich
> # neu anmelden. Da der KC_HOSTNAME-Wechsel das ohnehin verlangt, ist das
> # kein zusätzlicher Service-Impact.
> ```

1. `https://profit-base.online/` im Inkognito-Browser aufrufen.
2. "Login"-Button → muss zu `https://keycloak.profit-base.online/realms/iri/protocol/openid-connect/auth?...` umleiten.
3. Login mit echtem Account (oder einem dedizierten Test-Admin).
4. Nach Login: Redirect zurück zu `https://profit-base.online/...`, eingeloggt, Header zeigt "Profit Basetool".
5. Mindestens eine geschützte Seite besuchen (Mission, Hangar).
6. Browser-DevTools → Netzwerk-Tab → eine `/api/v1/...`-Request anklicken → "Headers"-Subtab → den `Authorization: Bearer <token>`-Header kopieren → den `<token>`-Teil (drei Punkt-getrennte Base64-Segmente) auf <https://jwt.io> einfügen → im decodierten Payload muss `"iss": "https://keycloak.profit-base.online/realms/iri"` stehen. Wenn dort noch der alte Issuer steht: zurück zu 7.3, prüfen ob Backend-Log auch den neuen Issuer zeigt.
7. Logout → muss sauber zurück zur Login-Seite oder zur Home-Page.

### 8.3 Maintenance-Page-Smoke-Test

```bash
sudo -u deploy docker compose --profile prod stop frontend
curl -i https://profit-base.online/ 2>&1 | head -5
# Erwartet: HTTP/1.1 503 + maintenance.html
curl -i -H 'Accept: application/json' https://profit-base.online/api/v1/missions 2>&1 | head -5
# Erwartet: HTTP/1.1 503 + application/problem+json mit
# "type": "https://profit-base.online/problems/maintenance"

sudo -u deploy docker compose --profile prod up -d frontend
# Warten auf healthy.
```

---

## Phase 9 — Alte Domain auf 308-Redirect umstellen

Damit User mit alten Bookmarks oder gespeicherten Links nahtlos auf die neue Domain landen.

### 9.1 NPM-Proxy-Host für `iri-base.org` umstellen

→ NPM-Admin → Proxy Hosts → `iri-base.org` editieren → **Advanced** Tab → Custom nginx Configuration:

```nginx
location / {
    return 308 https://profit-base.online$request_uri;
}
```

Save. Test:

```bash
curl -sI https://iri-base.org/missions/abc 2>&1 | head -5
# Erwartet:
#   HTTP/1.1 308 Permanent Redirect
#   Location: https://profit-base.online/missions/abc
```

### 9.2 NPM-Proxy-Host für `keycloak.iri-base.org` umstellen

**Empfohlen** (nicht "optional"): Admin-Bookmarks auf `https://keycloak.iri-base.org/admin/` würden ohne Redirect ein TLS-Mismatch oder NPM-502 produzieren — Keycloak antwortet wegen `KC_HOSTNAME_STRICT=true` (siehe `docker-compose.yml` Z. 119) nur noch auf den neuen Hostname. OAuth2-User landen zwar nie direkt dort, aber jeder Admin-Direkt-Login schlägt fehl bis der Redirect existiert.

```nginx
location / {
    return 308 https://keycloak.profit-base.online$request_uri;
}
```

Test:

```bash
curl -sI https://keycloak.iri-base.org/admin/ 2>&1 | head -5
# Erwartet:
#   HTTP/1.1 308 Permanent Redirect
#   Location: https://keycloak.profit-base.online/admin/
```

### 9.3 Auto-Deploy wieder anschalten

```bash
sudo systemctl start iri-deploy.timer
sudo systemctl status iri-deploy.timer
# Erwartet: Active: active (waiting), nächster Tick in <=5 Minuten
```

### 9.4 Stabilisierungsphase

Empfohlen: 2–4 Wochen die alte Domain als 308-Redirect aktiv lassen, damit alle User ihre Bookmarks aktualisieren. Logs anschauen:

```bash
sudo -u deploy docker compose --profile prod logs npm | grep "iri-base.org" | tail -20
# Erwartet: Anzahl Requests sinkt über die Wochen.
```

---

## Phase 10 — Cleanup (nach 2–4 Wochen Stabilität)

### 10.1 Alte DNS-Records löschen

Beim Domain-Registrar: A/AAAA-Records für `iri-base.org` und `keycloak.iri-base.org` entfernen.

### 10.2 Alte NPM-Proxy-Hosts löschen

→ NPM-Admin → Proxy Hosts → `iri-base.org` und `keycloak.iri-base.org` → Delete.

### 10.3 Alte Let's-Encrypt-Certs sind dann automatisch ungenutzt

Bleiben in `/var/iri/npm/letsencrypt/` liegen, werden aber nicht mehr erneuert. Bei Bedarf:

```bash
sudo find /var/iri/npm/letsencrypt -name "*iri-base*" -delete
```

### 10.4 Alte Keycloak-Client-Redirect-URIs entfernen

In der Keycloak-Admin (jetzt unter `https://keycloak.profit-base.online/admin/`):
- Realm `iri` → Clients → `basetool-frontend`
- Valid Redirect URIs: `https://iri-base.org/*` und `https://iri-base.org/login/oauth2/code/keycloak` entfernen.
- Valid Post Logout Redirect URIs: analog.
- Web Origins: `https://iri-base.org` entfernen.

Save + neuen Full-Export via `kc.sh export` ziehen und auf den Host kopieren (gleicher Pfad wie Phase 6.2 — `realm-export.json` ersetzen, alte Datei vorher als `.backup-cleanup` sichern).

### 10.5 Backup-Files entfernen (optional)

```bash
sudo find /var/iri/backups -name "*-$(date -d '30 days ago' +%Y%m)*" -delete
```

---

## Rollback-Pfad

Falls in Phase 5–8 etwas schiefgeht und die neue Domain nicht startet:

### R.1 Schneller Rollback per Image-Pin

```bash
# Im /var/iri/code/.env die Version auf den alten Release-Tag pinen. Achtung:
# das setup.md / .env.example sagt "Leave IRI_BASETOOL_VERSION unset" — d. h.
# die Variable existiert in der .env eines frisch bootgestrappten Hosts unter
# Umständen GAR NICHT. `sed -i` greift dann nicht. Robuster Pattern:
if sudo grep -q '^IRI_BASETOOL_VERSION=' /var/iri/code/.env; then
    sudo sed -i 's/^IRI_BASETOOL_VERSION=.*/IRI_BASETOOL_VERSION=1.4.X/' /var/iri/code/.env
else
    echo 'IRI_BASETOOL_VERSION=1.4.X' | sudo tee -a /var/iri/code/.env >/dev/null
fi
# 1.4.X = die letzte funktionierende Version (aus Phase 0.3 / 0.2-Backup-Dateinamen).

# docker-compose.yml zurückrollen — Backup-Datei aus Phase 0.2 nutzen:
ls -1t /var/iri/code/docker-compose.yml.backup-* | head -1   # zeigt das neueste Backup
sudo cp "$(ls -1t /var/iri/code/docker-compose.yml.backup-* | head -1)" /var/iri/code/docker-compose.yml

# Stack neu starten:
cd /var/iri/code
sudo -u deploy docker compose --profile prod pull
sudo -u deploy docker compose --profile prod up -d --wait
```

### R.2 Keycloak-Client-Config rollback

Falls in Phase 3 versehentlich alte Redirect-URIs entfernt wurden (Phase 3 sollte nur **ergänzen**, nicht ersetzen): zwei Pfade, je nachdem wie viel kaputt ist.

- **Schnellster Pfad (Browser):** in der Admin-UI die `https://iri-base.org/*`-Einträge wieder hinzufügen — Realm `iri` → Clients → `basetool-frontend` → Valid Redirect URIs / Post Logout Redirect URIs / Web Origins.
- **Disaster-Recovery (DB komplett zerschossen):** den Realm-Export aus dem Phase-0.2-Backup zurückspielen und Keycloak die Realm-Config neu importieren lassen:

  ```bash
  # Backup-Datei finden:
  ls -1t /var/iri/code/realm-export.json.backup-* | head -1
  sudo cp "$(ls -1t /var/iri/code/realm-export.json.backup-* | head -1)" /var/iri/code/realm-export.json
  # Achtung: der Import greift nur, wenn der iri-Realm in db-keycloak nicht
  # existiert. Falls doch (häufig), Realm-Config manuell in der Admin-UI fixen.
  ```

### R.3 DNS-Rollback

Da DNS-TTLs in Phase 0.1 auf 300s gesetzt sind, propagieren Korrekturen schnell. Falls nötig: A-Record für `profit-base.online` löschen, alte `iri-base.org`-Records bleiben unverändert aktiv.

### R.4 Auto-Deploy wieder anschalten

Auch im Rollback-Fall am Ende:

```bash
sudo systemctl start iri-deploy.timer
```

---

## Troubleshooting

| Symptom | Wo schauen | Wahrscheinliche Ursache |
| --- | --- | --- |
| Keycloak bleibt unhealthy nach Phase 7.2 | `docker compose logs keycloak` | DNS für `keycloak.profit-base.online` noch nicht propagiert → `KC_HOSTNAME_STRICT="true"` schlägt fehl. Lösung: Phase 0.1 abwarten. |
| Frontend 502 nach Phase 7.3 | `docker compose logs frontend` | Backend-Image noch alte Version (issuer-uri-Mismatch) oder Backend nicht healthy. Lösung: `docker compose ps` prüfen, Backend ggf. einzeln neustarten. |
| OAuth2-Login wirft `invalid_redirect_uri` | Browser-DevTools-Network-Tab | Phase 3.2 nicht durchgeführt oder Redirect-URI-Eintrag falsch geschrieben. Lösung: in Keycloak-Admin nochmal prüfen — `https://profit-base.online/login/oauth2/code/keycloak` muss exakt im Client stehen. |
| `iss` im JWT zeigt noch auf alte Domain | DevTools → JWT decoden | Backend hat noch alte `KEYCLOAK_ISSUER_URI`-Env-Var. Lösung: `docker compose logs backend \| grep "Keycloak issuer"` — muss `https://keycloak.profit-base.online/realms/iri` sein; sonst `docker-compose.yml`-Sync in Phase 6.1 nochmal prüfen + Backend restart. |
| Tests in CI laufen mit alter URL durch | GitHub-Actions-Log | Test-Assertions wurden auf alte URL gepinnt aber im PR vergessen umzustellen. Aktuell sind sie auf `profit-base.online` umgestellt; falls neue Tests dazukommen, müssen sie konsistent bleiben. |
| Maintenance-Page zeigt alte `IRIDIUM // DAS KARTELL` | NPM-Volume-Mount | `docker/maintenance/static/maintenance.html` wurde nicht in Phase 6.3 mit-aktualisiert. Lösung: Datei auf den Host syncen, dann `docker compose restart npm`. |
| `docker compose pull` antwortet mit `401 Unauthorized` | `journalctl -u iri-deploy.service` oder direkt `docker compose pull` | GHCR-Pull-Token unter `/etc/iri/ghcr-pull-token` abgelaufen oder revoked. Lösung: neuen Fine-Grained-PAT in GitHub generieren (Scope `Packages: Read` auf `krt-iri/basetool`), `sudo install -m 0600 -o deploy -g deploy /dev/stdin /etc/iri/ghcr-pull-token <<< 'ghp_neuer_token'`, dann Pull erneut. Siehe `docs/deployment.md` § Token rotation. |
| Let's-Encrypt-Cert-Renewal schlägt in NPM fehl | `/var/iri/npm/letsencrypt/logs/letsencrypt.log` | Port 80 nicht durchgereicht (Firewall / Router), oder Rate-Limit (5 Certs / Domain / Woche bei Let's Encrypt). Lösung: bei Rate-Limit auf Staging-Endpoint warten oder Cloudflare-DNS-Challenge nutzen. Fehler kommt typisch in Phase 2.2 bei der allerersten Cert-Anforderung — DNS-Propagation in Phase 0.1 prüfen, dann erneut Save. |
| Smoke-Test in Phase 8.2 produziert 500 / Re-Auth-Loop nur im Standard-Browser, Inkognito klappt | Browser-DevTools → Application → Cookies | Spring-Session-Cookie `SESSION` aus der Pre-Migration-Zeit referenziert einen Redis-Eintrag mit altem JWT-`iss`. Im Standard-Browser ausloggen + Cookies löschen, oder Redis-DB einmal flushen (siehe Phase 8.2-Warnblock). |

---

## Pre-Maintenance-Window-Check (Ende Phase 3, bevor Phase 4 startet)

Diese Items sind die Vorbedingung für das Wartungsfenster — wenn einer offen ist, **nicht in Phase 4 weitergehen**:

- [ ] Backup-Files aus Phase 0.2 in `/var/iri/backups/` vorhanden und nicht 0 Byte (`ls -la /var/iri/backups/ | grep "$(date +%Y%m%d)"`)
- [ ] `dig +short profit-base.online` und `dig +short keycloak.profit-base.online` liefern beide die Prod-Host-IP (von einem externen Rechner aus geprüft)
- [ ] NPM-Proxy-Hosts für beide neue Hostnames angelegt, beide Let's-Encrypt-Certs ausgestellt (NPM-Admin zeigt "Online" + "Valid SSL")
- [ ] `curl -vI https://profit-base.online/ 2>&1 | grep "SSL certificate verify ok"` grünt durch
- [ ] Keycloak-Client `basetool-frontend` enthält **sowohl** alte `iri-base.org/*`- als **auch** neue `profit-base.online/*`-Redirect-URIs (in der Admin-UI doppelt-checken: Phase 3.2 sollte nur ergänzt, nicht ersetzt haben)
- [ ] PR #165 ist auf `main` gemerged und `release-images.yml` ist grün durchgelaufen (`ghcr.io/krt-iri/basetool-{backend,frontend}:1.5.0` existiert in GHCR)
- [ ] Wartungsfenster wurde per Discord / E-Mail / Announcement-Banner kommuniziert

---

## Verifikations-Checkliste (Phase 8 Ende)

- [ ] DNS: `dig profit-base.online` und `dig keycloak.profit-base.online` liefern Prod-Host-IP
- [ ] TLS: Browser zeigt grünes Schloss auf beiden neuen Hostnames, Cert-Issuer = Let's Encrypt
- [ ] Frontend Header zeigt "Profit Basetool"
- [ ] Maintenance-Page Footer zeigt "PROFIT // DAS KARTELL"
- [ ] Backend-Startup-Banner: "Profit Basetool :: backend ready"
- [ ] Backend-Log: `Keycloak issuer : https://keycloak.profit-base.online/realms/iri`
- [ ] Keycloak `/realms/iri/.well-known/openid-configuration` issuer = `https://keycloak.profit-base.online/realms/iri`
- [ ] Anonymer Backend-API-Aufruf → problem+json `type` zeigt auf `https://profit-base.online/problems/...`
- [ ] OAuth2-Login mit Test-Account erfolgreich
- [ ] JWT-`iss`-Claim im DevTools = `https://keycloak.profit-base.online/realms/iri`
- [ ] Geschützte Seite (Mission/Hangar) lädt nach Login
- [ ] Maintenance-Page-Trigger (Phase 8.3) funktioniert
- [ ] `iri-base.org/missions/123` → 308 → `profit-base.online/missions/123`
- [ ] `sudo systemctl status iri-deploy.timer` zeigt `active (waiting)`
