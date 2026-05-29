# Deployment Runbook - SC Wiki + UEX-Items Sync

Operational runbook for the staged production rollout of the SC Wiki + UEX-Items
sync (see [`SC_WIKI_SYNC_PLAN.md`](SC_WIKI_SYNC_PLAN.md) for the design and
[`SC_WIKI_SYNC_AGENT_PROMPT.md`](SC_WIKI_SYNC_AGENT_PROMPT.md) for the implementation
briefing). The work lands across **nine consolidated PRs** (R1 -> R9), deployed
in order with soak windows between phase transitions. This document is the
step-by-step guide for executing that rollout safely.

The structure mirrors [`SK_ROLLOUT_RUNBOOK.md`](SK_ROLLOUT_RUNBOOK.md). R1 is
fully written below; R2 - R9 are stubbed with their headers so the runbook
grows in lockstep with each phase PR. **Each phase PR extends its section
in this file as part of the same commit that ships the code.**

---

## Status table - which phase has shipped to which environment

Updated as each phase deploys. The "PR" column points to the merged Pull
Request; "Date" is the production deploy timestamp.

| Phase | Dev | Staging | Prod | Date | PR | Notes |
|---|---|---|---|---|---|---|
| R1 - Foundation | TBD | TBD | TBD | TBD | TBD | additive only; scheduler default off |
| R2 - UEX items + vehicle hardening | TBD | TBD | TBD | TBD | TBD | adds game_item table + UEX item walk |
| R3 - Wiki commodity merge | TBD | TBD | TBD | TBD | TBD | first Wiki sync goes live |
| R4 - Wiki items + Blueprints | TBD | TBD | TBD | TBD | TBD | blueprint graph land + closure-mode item fill |
| R5 - Full Wiki item backfill | TBD | TBD | TBD | TBD | TBD | feature-flagged backfill (~12 700 rows) |
| R6 - Manufacturer Wiki reconciliation | TBD | TBD | TBD | TBD | TBD | |
| R7 - UEX item prices | TBD | TBD | TBD | TBD | TBD | feature-flagged; off by default |
| R8 - Soak + V115 cleanup | TBD | TBD | TBD | TBD | TBD | flips is_manual_entry -> source_systems=MANUAL |
| R9 - V116 destructive cleanup | TBD | TBD | TBD | TBD | TBD | drops is_manual_entry + ship_type.description |

---

## 0. Conventions

- Cite every env var by name and where it is set (Docker Compose env file,
  GitHub Actions secrets, runtime override).
- For every "run X" step, give the exact command, the expected output, and
  what to do if it diverges.
- Every irreversible step gets its own **Rollback** subsection so the on-call
  engineer never has to derive the reversal under pressure.
- All paths in this runbook are relative to the repo root unless prefixed
  with `prod:` (the production host filesystem).
- Times are UTC. Backups carry a `YYYYMMDD-HHMM` UTC timestamp suffix.

### 0.1 Tools / prerequisites

- `./gradlew` (project wrapper - never the IDE / global gradle).
- `docker` + `docker compose` plugin v2+ for the local test stack.
- `psql` 18+ client.
- `gh` CLI (authenticated as a repo collaborator) for the PR / status checks.
- `.env.test` at the repo root with throwaway credentials and a locally
  generated `keystore.p12` (README's "Running the Local Test Stack" has the
  exact commands). **NEVER use the production `.env` for staging.**

### 0.2 Stage on `.env.test` first

Before every prod deploy in this rollout, replay the migration stack against
a locally restored prod snapshot:

```bash
docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml \
    --profile dev up -d db-backend-dev
# restore the prod snapshot into the throwaway DB
psql -h localhost -p 15432 -U test -d basetool < prod-snapshot.sql
# run Flyway via the app boot (Ctrl-C after migrations apply)
./gradlew :backend:bootRun
```

Confirm the migrations apply cleanly and `ddl-auto: validate` does not log
any drift. Tear the test stack down with
`docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml --profile dev down --volumes`
afterwards.

### 0.3 Backup posture per phase

- **R1, R2, R3, R4, R6, R7** - additive migrations only; **logical backup
  (`pg_dump --format=custom`) is sufficient** as defence against an app-layer
  regression that corrupts existing rows during a dual-write window.
- **R5** - large data backfill (~12 700 Wiki item rows); take a logical
  backup of `game_item` + `manufacturer` immediately before the deploy so
  the rollback is a simple table re-load.
- **R8** - data-only migration (V115 backfills `source_systems = 'MANUAL'`
  from `is_manual_entry`); logical backup of `material` only.
- **R9** - destructive (drops `material.is_manual_entry` and
  `ship_type.description`); requires **both** a logical dump AND a
  `pg_basebackup` for point-in-time recovery.

Verification is mandatory: restore the dump into a throwaway PostgreSQL
container and run a count check against the aggregate tables that this phase
touches (e.g. R3 verifies `material`, `material_external_alias`). If the
counts diverge from prod, the dump is suspect and the deploy must be aborted.

---

## 1. R1 - Foundation deployment

### 1.1 Scope summary

R1 ships **plumbing only** with zero behaviour change to existing users:

- Flyway migrations **V106 - V109**:
  - **V106** - 10 additive columns on `material` (Wiki cross-ref, density,
    `is_visible`, `source_systems`) + `UNIQUE(scwiki_uuid)` + indexes on
    `source_systems` / `is_visible`. Defaults `is_visible = TRUE`,
    `source_systems = 'UEX_ONLY'` to preserve the pre-Wiki catalogue's
    visibility.
  - **V107** - 10 additive columns on `manufacturer` (UEX company id, Wiki
    UUID, industry, `is_item_manufacturer`, sync timestamps) + 2 UNIQUE
    constraints.
  - **V108** - new `material_external_alias` table + index + unique
    constraint, plus 6 SC Wiki -> UEX seed aliases (conditional on the
    target material existing; on a fresh DB without UEX sync data the
    seed is a no-op).
  - **V109** - new `uex_category` reference table (empty; populated by the
    R2 `UexCategoryRefService`).
- New `integration/scwiki/` package: `ScWikiClient` (paginated, ETag-cached,
  rate-limit-paced), `ScWikiScheduler` skeleton (no-op while
  `krt.scwiki.scheduler-enabled` is `false`).
- `ScWikiProperties` (`krt.scwiki.*`) with R1 default `scheduler-enabled = false`.
- `AsyncConfig.SCWIKI_EXECUTOR` thread pool (size 2, queue 0, MDC-propagating).
- Admin CRUD at `/admin/material-aliases` (Thymeleaf page + REST at
  `/api/v1/material-external-aliases`).
- ArchUnit guard: every class in `integration.scwiki` must inject
  `ScWikiClient`.
- Tests: WireMock-driven `ScWikiClientTest`, Mockito
  `MaterialExternalAliasServiceTest`, MockMvc
  `MaterialExternalAliasControllerTest`, TestContainers
  `V106 / V107 / V108 / V109 MigrationTest`.

**No live SC Wiki traffic** is generated by R1 - the scheduler is disabled
by default and no sync services consume its tick.

### 1.2 Pre-deployment checks (R1)

- [ ] Production is on `main` (commit ada0477c or later).
- [ ] `SELECT version FROM flyway_schema_history ORDER BY version DESC LIMIT 5`
      on prod shows V105 as the latest applied migration; no V106+ entries.
- [ ] `./gradlew spotlessApply check` is green from a clean clone of the R1 PR
      branch (Checkstyle 0 warnings, SpotBugs 0 findings, every test passes).
- [ ] The local test stack reaches startup with the R1 branch:
      `docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml --profile dev up -d`
      then check `docker compose logs backend-dev | grep "Started BackendApplication"`.
- [ ] `psql` against the local test stack confirms Flyway applied V106 - V109
      (`SELECT version FROM flyway_schema_history WHERE version IN ('106','107','108','109')`).
- [ ] `.env` on prod does NOT set `KRT_SCWIKI_SCHEDULER_ENABLED=true` (or any
      variant). Leaving it unset keeps R1's safe default.

### 1.3 Deployment steps (production, R1)

1. **Take the logical backup.**
   ```bash
   pg_dump --host=<prod-host> --username=<prod-user> --format=custom \
     --no-owner --no-acl \
     --file=basetool-pre-r1-$(date -u +%Y%m%d-%H%M).dump basetool
   ```
   Verify the dump opens:
   ```bash
   pg_restore --list basetool-pre-r1-*.dump | head -20
   ```

2. **Merge the R1 PR to `main`.**

3. **Watch the prod deploy logs** for the Flyway lines applying V106 - V109.
   Expected sequence in `logs/backend.json`:
   ```
   Successfully validated 105 migrations (execution time 00:00.NNN)
   Current version of schema "public": 105
   Migrating schema "public" to version "106 - add scwiki columns to material"
   Migrating schema "public" to version "107 - add cross ref columns to manufacturer"
   Migrating schema "public" to version "108 - create material external alias"
   Migrating schema "public" to version "109 - create uex category"
   Successfully applied 4 migrations to schema "public", now at version v109
   ```

4. **Confirm the application started** without `ddl-auto: validate` failures:
   ```
   Started BackendApplication in N.NNN seconds
   ```
   A schema-validation failure would show as
   `Schema-validation: missing column [scwiki_uuid] in table [material]` -
   that would mean V106 did not apply, abort and roll back per §1.5.

### 1.4 Smoke tests (R1, post-deploy)

Run these on the prod host immediately after the deploy finishes.

```bash
# 1. New columns are visible on material.
psql -d basetool -c "\d material" | grep -E 'scwiki_uuid|source_systems|is_visible'
# Expect: three rows, one per column.

# 2. material_external_alias table exists; seed row count (depends on whether
#    UEX-sourced materials were present at Flyway-run time).
psql -d basetool -c "SELECT count(*) FROM material_external_alias WHERE created_by='system'"
# Expect: 6 on a healthy prod (UEX commodity sync has populated the materials).
#         0 on a fresh staging DB - add aliases via the admin UI.

# 3. uex_category table exists, empty.
psql -d basetool -c "SELECT count(*) FROM uex_category"
# Expect: 0 (R2 populates it).

# 4. Admin API accepts a list call (with an admin JWT).
curl -fsS -H "Authorization: Bearer <admin-jwt>" \
  https://<host>/api/v1/material-external-aliases | head -c 200
# Expect: JSON array (may be empty).

# 5. Admin page renders.
curl -fsS -H "Cookie: <session-cookie>" \
  https://<host>/admin/material-aliases | grep -c 'admin.materialAlias.title'
# Expect: >= 1 (the i18n key resolves in the rendered HTML).

# 6. UEX scheduler is still alive.
docker compose logs backend | grep "Scheduled task for UEX data" | tail -1
# Expect: a recent timestamp within the past hour.

# 7. SC Wiki scheduler is alive but disabled (logged INFO line).
docker compose logs backend | grep -E "ScWikiScheduler.*disabled"
# Expect: at least one match (the scheduler fires immediately at boot, then
#         again every 24h; the disabled guard short-circuits with the log).
```

### 1.5 Rollback (R1)

R1 migrations are all additive and the application code carries no `ddl-auto:
update` machinery, so the rollback is a clean column / table drop in reverse
order.

1. **Revert the merge** on `main` to restore the pre-R1 code.
2. Drop the new tables and columns:
   ```sql
   DROP TABLE IF EXISTS uex_category;            -- V109
   DROP TABLE IF EXISTS material_external_alias; -- V108
   ALTER TABLE manufacturer
     DROP COLUMN IF EXISTS uex_company_id,
     DROP COLUMN IF EXISTS scwiki_uuid,
     DROP COLUMN IF EXISTS scwiki_code,
     DROP COLUMN IF EXISTS industry,
     DROP COLUMN IF EXISTS is_item_manufacturer,
     DROP COLUMN IF EXISTS is_vehicle_manufacturer,
     DROP COLUMN IF EXISTS uex_synced_at,
     DROP COLUMN IF EXISTS scwiki_synced_at,
     DROP COLUMN IF EXISTS uex_deleted_at,
     DROP COLUMN IF EXISTS scwiki_deleted_at;     -- V107
   ALTER TABLE material
     DROP COLUMN IF EXISTS scwiki_uuid,
     DROP COLUMN IF EXISTS scwiki_key,
     DROP COLUMN IF EXISTS scwiki_slug,
     DROP COLUMN IF EXISTS scwiki_synced_at,
     DROP COLUMN IF EXISTS scwiki_deleted_at,
     DROP COLUMN IF EXISTS density_g_per_cc,
     DROP COLUMN IF EXISTS instability,
     DROP COLUMN IF EXISTS resistance,
     DROP COLUMN IF EXISTS is_visible,
     DROP COLUMN IF EXISTS source_systems;       -- V106
   DELETE FROM flyway_schema_history WHERE version IN ('106','107','108','109');
   ```
3. Restart the backend. `ddl-auto: validate` should pass against the pre-R1
   entity shape (which is the code that just re-deployed).

If the restart fails to validate, restore the pre-R1 `pg_dump` instead.

### 1.6 Monitoring during the soak window (R1)

The R1 soak is **>= 1 release** before R2 merges. Watch for:

- `logs/backend.json` `level=ERROR` count - baseline before R1 vs. baseline
  after. The R1 add is plumbing only; an error-rate climb means an
  unexpected interaction with `ddl-auto: validate` or the new entity fields.
- Hibernate validation log lines on every restart - none expected.
- `ScWikiScheduler invoked but disabled` INFO lines once per 24 h - if these
  vanish, the scheduler bean is not loading; check
  `@ConditionalOnProperty` wiring (R1 has none, but R2+ may add some).
- Admin pages: `/admin/material-aliases` opens and lists the 6 seed rows
  (or accepts a manual add when the seed was a no-op).

---

## 2. R2 - UEX item catalogue + UEX manufacturer / vehicle hardening

*(Stub - written when R2 lands. Adds V110 - V112 migrations,
`UexItemSyncService`, `UexCategoryRefService`, `UexVehicleService` UUID
hardening. No Wiki side touched yet.)*

### 2.1 Scope summary

TBD.

### 2.2 Pre-deployment checks (R2)

TBD.

### 2.3 Deployment steps (production, R2)

TBD.

### 2.4 Smoke tests (R2, post-deploy)

TBD.

### 2.5 Rollback (R2)

TBD.

### 2.6 Monitoring during the soak window (R2)

TBD.

---

## 3. R3 - Wiki commodity merge

*(Stub - written when R3 lands. First phase that flips
`krt.scwiki.scheduler-enabled` to `true`. Adds the junk filter, fuzzy seed
verification, and the sync-report admin page.)*

### 3.1 Scope summary

TBD.

### 3.2 Pre-deployment checks (R3)

TBD.

### 3.3 Deployment steps (production, R3)

TBD.

### 3.4 Smoke tests (R3, post-deploy)

TBD.

### 3.5 Rollback (R3)

TBD.

### 3.6 Monitoring during the soak window (R3)

TBD.

---

## 4. R4 - Wiki items + Blueprints

*(Stub - V113 migration adds the blueprint graph. Wiki item sync runs in
closure mode; Wiki vehicle sync fills the columns added in V111.)*

### 4.1 Scope summary

TBD.

### 4.2 Pre-deployment checks (R4)

TBD.

### 4.3 Deployment steps (production, R4)

TBD.

### 4.4 Smoke tests (R4, post-deploy)

TBD.

### 4.5 Rollback (R4)

TBD.

### 4.6 Monitoring during the soak window (R4)

TBD.

---

## 5. R5 - Full Wiki item backfill

*(Stub - flips `krt.scwiki.sync-all-items=true` on a single environment
first. ~12 700 Wiki items get paged in; ~5000 already exist via UEX seed.)*

### 5.1 Scope summary

TBD.

### 5.2 Pre-deployment checks (R5)

TBD.

### 5.3 Deployment steps (production, R5)

TBD.

### 5.4 Smoke tests (R5, post-deploy)

TBD.

### 5.5 Rollback (R5)

TBD.

### 5.6 Monitoring during the soak window (R5)

TBD.

---

## 6. R6 - Manufacturer Wiki reconciliation

*(Stub - adds `ScWikiManufacturerSyncService`. Fills `scwiki_uuid` /
`scwiki_code` on rows where UEX has set `uex_company_id`.)*

### 6.1 Scope summary

TBD.

### 6.2 Pre-deployment checks (R6)

TBD.

### 6.3 Deployment steps (production, R6)

TBD.

### 6.4 Smoke tests (R6, post-deploy)

TBD.

### 6.5 Rollback (R6)

TBD.

### 6.6 Monitoring during the soak window (R6)

TBD.

---

## 7. R7 - UEX item prices

*(Stub - V114 adds `game_item_price`. Sync service is feature-flagged and
off by default; enabled only once a UI surface needs the data.)*

### 7.1 Scope summary

TBD.

### 7.2 Pre-deployment checks (R7)

TBD.

### 7.3 Deployment steps (production, R7)

TBD.

### 7.4 Smoke tests (R7, post-deploy)

TBD.

### 7.5 Rollback (R7)

TBD.

### 7.6 Monitoring during the soak window (R7)

TBD.

---

## 8. R8 - Soak + V115 cleanup

*(Stub - V115 backfills `is_manual_entry=true -> source_systems='MANUAL'`.
Two-week soak window before R9 destructive cleanup runs.)*

### 8.1 Scope summary

TBD.

### 8.2 Pre-deployment checks (R8)

TBD.

### 8.3 Deployment steps (production, R8)

TBD.

### 8.4 Smoke tests (R8, post-deploy)

TBD.

### 8.5 Rollback (R8)

TBD.

### 8.6 Monitoring during the soak window (R8)

TBD.

---

## 9. R9 - V116 destructive cleanup

*(Stub - drops `material.is_manual_entry` and `ship_type.description`
synthesized column. Tracked separately, similar to
[`R8_DESTRUCTIVE_ROADMAP.md`](R8_DESTRUCTIVE_ROADMAP.md) for the SK work.)*

### 9.1 Scope summary

TBD - will cross-link to a dedicated `R9_DESTRUCTIVE_ROADMAP.md` document
when the destructive cleanup roadmap is written.

### 9.2 Pre-deployment checks (R9)

TBD.

### 9.3 Deployment steps (production, R9)

TBD.

### 9.4 Smoke tests (R9, post-deploy)

TBD.

### 9.5 Rollback (R9)

TBD.

---

## Appendix A - Common operations

### A.1 Manually re-run an SC Wiki sync

Once R3+ ships, the Wiki sync can be triggered out-of-band by flipping the
property at runtime:

```bash
# Tell the backend to re-read its properties (or restart the container).
docker compose restart backend
# Or expose an admin endpoint in R3+: POST /api/v1/admin/sync/scwiki/trigger
```

In R1 the scheduler bean exists but is gated by
`krt.scwiki.scheduler-enabled = false`; flipping it via env var
`KRT_SCWIKI_SCHEDULER_ENABLED=true` makes the tick fire (the body logs a
warning that no sync services are wired in yet - that is expected for R1).

### A.2 Read the sync report (R3+)

R3 ships `/admin/sync-reports/scwiki` and `/admin/sync-reports/uex`. R1 has
no sync report yet - the admin reviews the alias table directly at
`/admin/material-aliases`.

### A.3 Add a `material_external_alias` row by hand

Two paths:

1. **Admin UI** (preferred):
   - Open `/admin/material-aliases` as an `ADMIN` user.
   - Use the "Neuen Alias anlegen" form. Pick the local material, set the
     source (UEX / SCWIKI), enter the external name, optionally provide
     external key / UUID / code and a note explaining the verification
     provenance.
   - The submit creates the row, refreshes the list view.

2. **Direct SQL** (incident-only):
   ```sql
   INSERT INTO material_external_alias
     (id, material_id, source_system, external_name, note, created_by)
   VALUES (gen_random_uuid(),
           (SELECT id FROM material WHERE name = 'Construction Material Pebbles'),
           'SCWIKI',
           'Construction Pieces',
           'Manual alias after in-game grade verification on YYYY-MM-DD',
           '<your-jwt-sub>');
   ```

### A.4 Flip `material.is_visible` on a Wiki-only row (R3+)

```sql
UPDATE material SET is_visible = TRUE
 WHERE source_systems = 'WIKI_ONLY'
   AND name = '<verified-name>';
```

R3+ exposes the same toggle in the material edit page; the SQL form is for
batch flips during the post-Wiki-merge review.

### A.5 Force the V108 seed to apply on a DB where it was a no-op

If R1 deployed against a fresh DB where the UEX commodity sync had not run
yet, the V108 seed inserted zero alias rows (the
`INSERT ... SELECT ... WHERE m.name = ...` clause found no target). After
the next UEX hourly sync populates the six target materials
(`Silicon (Raw)`, `Stileron (Raw)`, `Ouratite (Raw)`, `Hephaestanite (Raw)`,
`Lastaphrene`, `Lunes`), re-apply the seed manually:

```sql
\i backend/src/main/resources/db/migration/V108__create_material_external_alias.sql
```

The `INSERT ... ON CONFLICT DO NOTHING` clauses keep the re-run idempotent
against rows the admin already added by hand.

---

## Appendix B - Failure modes catalog

### B.1 SC Wiki API returns 5xx for an entire sync run

**Symptom.** `ScWikiScheduler` log lines show
`Failed to fetch <resource> from SC Wiki API (...)` for every endpoint in
the cycle; the merged data list is empty.

**Behaviour.** Sync services consume the empty list as "skip this run" and
do NOT mark local rows as deleted (the orphan-handling sweep is gated on a
non-empty seen-id set, mirroring the UEX `clearStalePrices` pattern).

**Recovery.** Nothing to do. The next scheduled tick (default 24 h) retries.
If the outage extends beyond a release cycle, set the `Wiki API outage`
banner via `/admin/announcement` so users know catalog data is stale.

### B.2 UEX `/items` schema drift introduces a new field

R1 does not yet read `/items`. R2 ships
`@JsonIgnoreProperties(ignoreUnknown = true)` on every UEX item DTO so an
upstream field add is transparent. A field rename / removal breaks the
mapping - the sync logs a Jackson-level deserialisation failure and skips
the affected rows.

### B.3 SC Wiki API rate-limits the sync (HTTP 429)

R1 paces at `krt.scwiki.requests-per-second = 5` (default). If 429s start
firing in R3+, drop the rate to 2 - 3 via an env override:

```bash
KRT_SCWIKI_REQUESTS_PER_SECOND=2 docker compose up -d backend
```

The setting is hot-reloadable only via a container restart in R1; R3+ may
ship a runtime hot-reload via `@RefreshScope`.

### B.4 Migration test fails on V108 seed integrity

If `V108MigrationTest.v108SeedInsertsCreateSixAliasRowsWhenTargetMaterialsExist`
fails with a different row count, the V108 SQL has been edited in a way that
either added a seed row (and the `SEED_PAIRS` constant in the test was not
updated) or dropped one. Update both in the same commit and re-run the test.

### B.5 ArchUnit rule fails on a freshly-renamed class

`sCWikiIntegrationClassesMustWireScWikiClient` fires when a class moves into
`integration.scwiki` without depending on `ScWikiClient`. Two fixes:

- The new class IS a sync service - inject `ScWikiClient` as a field.
- The new class is unrelated (utility / DTO holder) - move it out of the
  `integration.scwiki` package (most likely under `service.scwiki` or
  `dto.scwiki`).

---

## Appendix C - Pre-cutover checklist

Print this section before the prod merge. The on-call engineer signs each
item before approving the deploy.

- [ ] Migration test green on a copy of the prod schema
      (`./gradlew :backend:test --tests "V*MigrationTest"`).
- [ ] `./gradlew check` green from a clean clone.
- [ ] Schema-validate mode confirms `ddl-auto: validate` boots without
      drift (run `./gradlew :backend:bootRun` against the staged DB and
      `Ctrl-C` after the "Started BackendApplication" log line).
- [ ] Sync-report admin pages open without 500 (R3+ only; R1 has none).
- [ ] Feature flags set as expected for this phase
      (`krt.scwiki.scheduler-enabled = false` in R1; `true` from R3 on).
- [ ] Backup taken with `pg_dump --format=custom` and verified by listing
      its contents (`pg_restore --list`).
- [ ] CHANGELOG.md entry merged with the same PR.
- [ ] All translation keys present in DE and EN. For R1: spot-check that
      `grep '^admin.materialAlias.' frontend/src/main/resources/messages_en.properties | wc -l`
      and
      `grep '^admin.materialAlias.' frontend/src/main/resources/messages_de.properties | wc -l`
      match.
- [ ] On-call rotation knows the rollback playbook for this phase
      (link the §X.5 subsection in the Slack / e-mail notification).
