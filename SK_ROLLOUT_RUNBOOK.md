# Spezialkommando Rollout Runbook

Operational runbook for the staged production rollout of the Spezialkommando
extension (see [`SPEZIALKOMMANDO_PLAN.md`](SPEZIALKOMMANDO_PLAN.md) for the
design). The full code lands across **three consolidated PRs**, deployed in
order with soak windows between each. This document is the step-by-step guide
for executing that rollout safely.

## TL;DR

| # | PR | Branch | Adds | Risk | Soak |
| -- | -- | -- | -- | -- | -- |
| 1 | [#226](https://github.com/krt-iri/basetool/pull/226) Foundation | `consolidate/spezialkommando-foundation` | V97–V100, OrgUnit + SpecialCommand entities, picker on 7 forms, admin pages, switcher | **Low** — fully additive + dual-write | ≥ 1 release |
| 2 | [#227](https://github.com/krt-iri/basetool/pull/227) Identity | `consolidate/spezialkommando-identity` | V101 promotion guard, JWT converter on memberships, flag-writes to membership row | **Medium** — touches auth path, has legacy fallback | ≥ 1 release |
| 3 | [#228](https://github.com/krt-iri/basetool/pull/228) Cleanup | `consolidate/spezialkommando-cleanup` | V102–V105, drops legacy `squadron` table + columns | **High** — irreversible without DB backup | n/a (terminal) |

**Total deployable releases: 3. Total Flyway migrations: 9 (V97–V105). Total soak windows: 2.**

The rule of thumb: **never merge two stages back-to-back.** Each stage needs
its own deploy and its own soak before the next merges.

---

## 0. Pre-flight (before merging anything)

Before touching any of the three PRs, confirm the following baselines.

### 0.1 Production state

- [ ] Production is on the latest `main` (commit `604e81e4` or later — V94 manual-material, V95 quantity-type backfill, V96 mission-participant-unique-index are all on main; SK migrations start at V97).
- [ ] `SELECT version FROM flyway_schema_history ORDER BY version DESC LIMIT 5` returns the expected set on prod; no V97+ entries.
- [ ] DB version: PostgreSQL 18+. Java runtime: 25. Keycloak: 26.
- [ ] Redis is up and responding (Spring Session depends on it).
- [ ] `.env.test` exists locally with throwaway credentials (per
  [`feedback_env_test_isolation`](C:\Users\lucas\.claude\projects\D--NC-Software-Coding-Java-KRT-basetool\memory\feedback_env_test_isolation.md))
  — never use the production `.env` for staging the migrations.
- [ ] Backup procedure is rehearsed (see [`docs/deployment.md`](docs/deployment.md) §Backup).

### 0.2 Local sanity check

In a fresh checkout of `main`, before the PR-1 branch is merged:

```bash
git fetch origin
git checkout consolidate/spezialkommando-foundation
./gradlew :backend:test :frontend:test :backend:checkstyleMain :backend:spotbugsMain :frontend:checkstyleMain :frontend:spotbugsMain
```

Expected: `BUILD SUCCESSFUL` on all six tasks. If anything fails locally, do
not proceed — investigate the failure first. Two tests
(`HangarIntegrationTest.testUserCannotManageOtherHangar`,
`RefineryOrderTest.testUserCreateAndManageRefineryOrder`) are known
pre-existing flakes that fail on the R9 branch too — they are
**out of scope** for the rollout and acknowledged in [#228](https://github.com/krt-iri/basetool/pull/228).

### 0.3 Stage on `.env.test` first

Before each deploy, you should apply the same migration stack on a local
PostgreSQL with a snapshot of prod schema:

```bash
docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml --profile dev up -d db-backend-dev
# restore your prod schema snapshot into the throwaway DB
# then run the migrations the app would run on startup:
./gradlew :backend:bootRun  # will execute Flyway on startup, then you can Ctrl-C
```

Confirm Flyway applies the migrations cleanly. Tear the test stack down with
`docker compose ... down --volumes` afterwards.

### 0.4 Re-base awareness

PR-2 is stacked on PR-1. PR-3 is stacked on PR-2. The base of each PR must
be retargeted to `main` **after** the previous PR merges. The runbook calls
this out at each stage.

---

## 1. Stage 1 — PR-1 Foundation

**PR:** [#226](https://github.com/krt-iri/basetool/pull/226)
**Branch:** `consolidate/spezialkommando-foundation`
**Base:** `main` (already correct)
**Commits:** 26 (R1 → R5.e + R6.a-c + CHANGELOG cleanup tail + rebase onto current main + CodeQL XSS sink wrap)
**Migrations:** V97 V98 V99 V100 (all additive — table creation + nullable columns + sync trigger)

### 1.1 What it adds

- **Schema:** `org_unit` parent table (Single-Table-Inheritance with `kind` discriminator),
  `org_unit_membership` (n:m User ↔ OrgUnit with per-membership flags),
  nullable `owning_org_unit_id` / `creating_org_unit_id` / `requesting_org_unit_id`
  columns next to the legacy `owning_squadron_id` columns on every staffel-scoped
  aggregate. One-way sync trigger keeps `squadron` table populated for FK
  resolution.
- **Java:** New `OrgUnit` / `SpecialCommand` / `OrgUnitMembership` entities.
  `Squadron` becomes `@DiscriminatorValue("SQUADRON")` subclass of `OrgUnit`.
  New `OwnerScopeService`. Lifecycle hook on every aggregate dual-writes
  `owningSquadron ↔ owningOrgUnit`.
- **REST API:** New `/api/v1/special-commands` CRUD, membership-management
  endpoints, `/api/v1/users/{id}/memberships`, `/api/v1/org-units/active`.
- **UI:** Owner-picker fragment on 7 create/update forms (inventory-input,
  refinery-orders-create, orders-create + orders-detail, mission-create,
  operation-create, hangar add-ship, inventory-transfer TRANSFER). Picker
  **stays hidden when caller has ≤1 membership** — every existing user sees
  no UI change today. Admin pages `/admin/special-commands` and per-SK detail.
  Active-context switcher widened to any non-admin with >1 membership.
- **Wire protocol:** `X-Active-Squadron-Id` → `X-Active-Org-Unit-Id` header
  rename, both honored as alias for one release. `iridium.activeSquadronId`
  session attribute renamed in lockstep.

### 1.2 Pre-merge checks

- [ ] CI is green on PR #226 (CodeQL, DCO, Build/Test/Lint).
- [ ] Diff stat sanity check: ~176 files, +11k / -1.5k. If GitHub shows
  drastically more, something is off.
- [ ] Conflicts with `main`: GitHub shows "This branch has no conflicts with
  the base branch". If there are conflicts (someone landed something to
  `main` since 2026-05-24), rebase the branch locally:
  ```bash
  git fetch origin
  git checkout consolidate/spezialkommando-foundation
  git rebase origin/main
  ./gradlew :backend:test :frontend:test  # re-verify
  git push --force-with-lease origin consolidate/spezialkommando-foundation
  ```

### 1.3 Merge

- Pick **squash merge** or **merge commit** based on team convention. Either
  is fine — the 26 commits are all yours, no foreign authors to preserve.
- After merge: do **not** delete the branch yet. Keep
  `consolidate/spezialkommando-foundation` until PR-3 has been deployed
  (forensic backup if a rollback is needed).

### 1.4 Deploy

Follow [`docs/deployment.md`](docs/deployment.md) standard release
procedure. Key signals to watch:

- **Backend startup logs:** Flyway should report
  `Migrating schema "public" to version "97 - ..."` ... up through `"100 - ..."`.
  Total time: < 5 seconds on a typical squadron table (sub-megabyte).
- **No `ddl-auto` warnings:** Hibernate is configured to `validate`. Any
  "Schema validation failed" line is a deal-breaker — STOP, investigate.
- **App `/actuator/health` → `UP`** within 30 seconds of startup.

### 1.5 Post-deploy smoke (manual, 10 minutes)

Run these against the deployed prod (or pre-prod equivalent):

| Step | Expected |
| -- | -- |
| `SELECT COUNT(*) FROM org_unit` | Matches `(SELECT COUNT(*) FROM squadron)`. |
| `SELECT COUNT(*) FROM org_unit_membership WHERE kind = 'SQUADRON'` | Matches `(SELECT COUNT(*) FROM app_user WHERE squadron_id IS NOT NULL)`. |
| `SELECT COUNT(*) FROM mission WHERE owning_org_unit_id IS NULL` | `0` (V99 backfill populated every row). |
| Same for operation, ship, inventory_item, refinery_order | `0` each. |
| `SELECT COUNT(*) FROM job_order WHERE creating_org_unit_id IS NULL OR requesting_org_unit_id IS NULL` | `0`. |
| `SELECT proname FROM pg_proc WHERE proname = 'sync_org_unit_to_squadron'` | `1` (V100 trigger function present). |
| Existing Staffel-only user logs in via Keycloak → app | Identical UX. |
| Open `/inventory/input` as that user | Picker NOT visible (single membership). |
| Open `/admin/special-commands` as ADMIN | Empty list, "Anlegen" button visible. |
| Admin creates an SK ("Test-SK", shorthand "TST") | Appears in list. |
| Admin opens detail page, adds a user as member | Member appears in roster. |
| That member logs in | Sidebar context switcher now visible (user has 2 memberships); chip shows `[Staffel: ...]` by default. |
| Member picks SK in switcher | Inventory list shows only SK-owned items (empty today). |
| Member creates inventory item — picker visible | Item lands with `owning_squadron_id` = home Staffel **and** `owning_org_unit_id` = picked OrgUnit (the dual-write). If they picked the SK, the create currently 400s with "Spezialkommando ownership not yet supported" — that's expected; PR-3 unlocks it. |

If any of these fail unexpectedly, refer to §1.7 rollback.

### 1.6 Soak window

**Minimum 1 release cycle** (≈ 1 week in our cadence). Monitor:

- Error logs for `500` responses on routes that touch
  `OwnerScopeService` / `SpecialCommandController` /
  `OrgUnitMembershipController`.
- Optimistic-locking `409` responses from
  `/api/v1/special-commands/*/members/*` — frontend may need a reload-on-conflict.
- JWT claim-parsing exceptions (`CustomJwtGrantedAuthoritiesConverter`).
- Page-render exceptions on routes that use the picker fragment.
- Background scheduled tasks (UEX sync, mission presence cleanup) — they
  shouldn't be touched by SK code, but a regression check is cheap.

**Before approving PR-2**, sign off that:

- No production incidents traced back to SK code.
- `org_unit` table is consistent with `squadron` (run the COUNT queries
  again).
- New SK admin pages work as expected.

### 1.7 Rollback (PR-1 only — easy, additive)

If PR-1 needs to be reverted before PR-2 lands:

1. Revert the merge commit:
   ```bash
   git revert -m 1 <PR-1-merge-commit>
   git push origin main
   ```
2. Re-deploy.
3. **The schema changes (V97–V100) stay applied** — they're additive, the
   reverted app code simply stops writing/reading the new tables and
   columns. The legacy `squadron` table is still authoritative.
4. To roll back the **schema** too (rare — only if the new tables are causing
   issues by their mere presence):
   ```sql
   -- Drop the V100 sync trigger first
   DROP TRIGGER IF EXISTS trg_sync_org_unit_to_squadron ON org_unit;
   DROP FUNCTION IF EXISTS sync_org_unit_to_squadron();

   -- Drop the new columns on aggregates (V99)
   ALTER TABLE mission         DROP COLUMN IF EXISTS owning_org_unit_id;
   ALTER TABLE operation       DROP COLUMN IF EXISTS owning_org_unit_id;
   ALTER TABLE ship            DROP COLUMN IF EXISTS owning_org_unit_id;
   ALTER TABLE inventory_item  DROP COLUMN IF EXISTS owning_org_unit_id;
   ALTER TABLE refinery_order  DROP COLUMN IF EXISTS owning_org_unit_id;
   ALTER TABLE job_order       DROP COLUMN IF EXISTS creating_org_unit_id;
   ALTER TABLE job_order       DROP COLUMN IF EXISTS requesting_org_unit_id;

   -- Drop the membership table (V98)
   DROP TABLE org_unit_membership;

   -- Drop the org_unit table (V97)
   DROP TABLE org_unit;

   -- Remove the migration history entries (so a re-deploy doesn't refuse)
   DELETE FROM flyway_schema_history WHERE version IN ('97', '98', '99', '100');
   ```
   This is **not** something Flyway can do for you — it must be a manual
   SQL session on the prod DB. Take a backup first.

---

## 2. Stage 2 — PR-2 Identity Migration

**PR:** [#227](https://github.com/krt-iri/basetool/pull/227)
**Branch:** `consolidate/spezialkommando-identity`
**Base on disk:** `consolidate/spezialkommando-foundation` (will be retargeted)
**Commits:** 4 (R6.d + R6.e + R7-sweep + doc V-sweep)
**Migrations:** V101 promotion-topic-guard trigger (no schema drops, no data changes)

### 2.1 Retarget the base

After PR-1 has merged and deployed and soaked successfully:

```bash
gh pr edit 227 --base main
```

This tells GitHub to compute the diff against `main` instead of against the
now-merged foundation branch.

If the merge type for PR-1 was "squash", the foundation branch's commits no
longer exist as ancestors of `main`. The PR-2 branch carries them as its own
prefix, so GitHub will detect them as part of PR-2's diff. To clean up:

```bash
git fetch origin
git checkout consolidate/spezialkommando-identity
git rebase origin/main
# resolve any conflicts (none expected if PR-1 merged cleanly)
./gradlew :backend:test :frontend:test
git push --force-with-lease origin consolidate/spezialkommando-identity
```

After the rebase, PR-2's diff should show only the 4 identity-stage commits.
If GitHub still shows the 23 foundation commits, the rebase didn't run.
Confirm via:

```bash
git log --oneline origin/main..consolidate/spezialkommando-identity
# Expected: 4 commits (R6.d, R6.e, R7-sweep, V-sweep). Not 27.
```

### 2.2 What it adds

- **Schema:** V101 trigger `guard_promotion_topic_owner_kind` that rejects any
  `INSERT/UPDATE` of `promotion_topic.owning_squadron_id` referencing a non-`SQUADRON`
  org_unit. Defence-in-depth on top of the Java type guard.
- **JWT layer:** `CustomJwtGrantedAuthoritiesConverter` now resolves
  `ROLE_LOGISTICIAN` / `ROLE_MISSION_MANAGER` from the **union** of a user's
  OrgUnit memberships (`org_unit_membership.is_logistician` /
  `is_mission_manager`), not from the legacy `app_user` columns. Legacy
  columns are read as a fallback for users with zero membership rows
  (pre-V96-backfill edge case).
- **Write path:** Legacy `PATCH /api/v1/users/{id}/logistician` etc.
  endpoints still work and **mirror** the flag-flip onto the Staffel
  membership row. New canonical endpoint
  `PATCH /api/v1/squadrons/{id}/members/{userId}` for direct membership-row
  patches with optimistic lock. `UserService.updateUserSquadron` syncs the
  Staffel-membership row when a user's Staffel changes.
- **UI:** Sidebar context chip labels the OrgUnit kind (`[Staffel: IRI]` /
  `[SK: ALPHA]`). Member-edit page shows a read-only memberships overview.
  Member list gains an "SK" column.
- **Endpoint:** `GET /api/v1/me/active-org-unit` as canonical alias to legacy
  `/api/v1/me/active-squadron`.

### 2.3 Pre-merge checks

- [ ] PR-1 has been deployed and soaked, no incidents.
- [ ] PR-2 retargeted to `main` (per §2.1).
- [ ] CI is green on PR #227.
- [ ] Local re-verify after rebase: `./gradlew :backend:test :frontend:test` BUILD SUCCESSFUL.

### 2.4 Merge + deploy

- Merge PR #227 (squash or merge commit).
- Standard deploy.
- Backend startup: V101 applies in < 1 second (just creates one trigger + function).
- No new tables/columns to verify post-startup.

### 2.5 Post-deploy smoke (manual, 15 minutes)

| Step | Expected |
| -- | -- |
| `SELECT proname FROM pg_proc WHERE proname = 'guard_promotion_topic_owner_kind'` | `1`. |
| Attempt `INSERT INTO promotion_topic (owning_squadron_id, ...) VALUES ('<an SK uuid>', ...)` directly via SQL | Trigger raises EXCEPTION: `promotion_topic.owning_squadron_id ... resolves to org_unit kind=SPECIAL_COMMAND, only SQUADRON is allowed`. (Run this in a transaction with ROLLBACK.) |
| User with `app_user.is_logistician = true` AND no membership row yet — log in | JWT has `ROLE_LOGISTICIAN` (legacy fallback). |
| User with `is_logistician = true` AND a membership row with `is_logistician = true` — log in | JWT has `ROLE_LOGISTICIAN` (membership path). |
| User with `app_user.is_logistician = false` but `org_unit_membership.is_logistician = true` — log in | JWT has `ROLE_LOGISTICIAN` (membership is authoritative, R6.d behavior). |
| Admin uses new `PATCH /api/v1/squadrons/{id}/members/{userId}` endpoint | 200, flag flip works, user gets/loses role on next login. |
| Admin uses legacy `PATCH /api/v1/users/{id}/logistician?value=true` endpoint | 200, flag flip works, **AND** the membership row is updated (dual-write). |
| Sidebar shows `[Staffel: IRI]` chip on a Staffel pin | ✓ |
| Switch to SK pin → chip becomes `[SK: <shorthand>]` | ✓ |
| Member list (`/members`) shows SK column | Empty for single-Staffel users; populated for multi-membership. |
| `GET /api/v1/me/active-org-unit` returns same data as `/me/active-squadron` | Same `orgUnitId` value, different field name. |

### 2.6 Soak window

**Minimum 1 release cycle.** Monitor for:

- JWT role mismatches: users reporting "I can't see X anymore" or "I should
  not be able to do Y". Most likely root cause: a membership row was
  expected but not present.
- Authentication failures: `CustomJwtGrantedAuthoritiesConverter` exceptions
  in logs.
- 409s on the new membership-patch endpoint (optimistic-lock races between
  concurrent admin actions).
- Promotion-topic-related 500s after V101 (the trigger raises an EXCEPTION
  that should never fire under normal operation — if it does, someone is
  trying to bind a promotion topic to an SK, which is a bug).

**Before approving PR-3**, sign off that:

- All users see their expected roles.
- The dual-write between user-table flags and membership-row flags is
  in lockstep — pick 5 random users, check both rows match.

### 2.7 Rollback (PR-2 only)

1. Revert the merge commit, re-deploy.
2. The V101 trigger stays. To drop:
   ```sql
   DROP TRIGGER IF EXISTS trg_guard_promotion_topic_owner_kind ON promotion_topic;
   DROP FUNCTION IF EXISTS guard_promotion_topic_owner_kind();
   DELETE FROM flyway_schema_history WHERE version = '101';
   ```
3. After revert: JWT converter falls back to the legacy user-table flags
   (the code path is still present in PR-1 since R6.d's converter rewrite
   doesn't land until PR-2). The membership rows that were written via the
   dual-write **stay** — they're harmless; PR-1's code doesn't read them
   for role decisions.

---

## 3. Stage 3 — PR-3 Destructive Cleanup

**PR:** [#228](https://github.com/krt-iri/basetool/pull/228)
**Branch:** `consolidate/spezialkommando-cleanup`
**Base on disk:** `consolidate/spezialkommando-identity` (will be retargeted)
**Commits:** 8 (R8 + R9 Step 1-6 + plan-status doc + V-sweep + test-fix for two R9-surfaced regressions)
**Migrations:** V102 V103 V104 V105 — **all four are destructive and
collectively irreversible**.

### 3.1 ABSOLUTE PREREQUISITES

Before clicking Merge on PR-3:

- [ ] **PR-2 has been in production for at least one full release cycle** with
  zero incidents traced to identity layer.
- [ ] **Full DB backup** taken within the last 24 hours, **restorability
  verified** by restoring into a throwaway DB and running `SELECT COUNT(*)`
  on every aggregate table. This is non-negotiable: V103 and V105 cannot be
  reversed without restoring from backup.
- [ ] All staging environments are running PR-2 (or newer) so the migrations
  can be exercised at scale before prod.
- [ ] You have **scheduled downtime** or a maintenance window. The destructive
  migrations take longer than the additive ones — plan for 10–15 minutes of
  application unavailability while Flyway runs.
- [ ] **Pre-flight queries against prod (read-only)** all return 0:
  ```sql
  -- Every aggregate row has an org_unit owner already.
  SELECT COUNT(*) FROM mission        WHERE owning_org_unit_id IS NULL;
  SELECT COUNT(*) FROM operation      WHERE owning_org_unit_id IS NULL;
  SELECT COUNT(*) FROM ship           WHERE owning_org_unit_id IS NULL;
  SELECT COUNT(*) FROM inventory_item WHERE owning_org_unit_id IS NULL;
  SELECT COUNT(*) FROM refinery_order WHERE owning_org_unit_id IS NULL;
  SELECT COUNT(*) FROM job_order      WHERE creating_org_unit_id IS NULL;
  SELECT COUNT(*) FROM job_order      WHERE requesting_org_unit_id IS NULL;

  -- Every user with a squadron_id has a corresponding membership row.
  SELECT COUNT(*) FROM app_user u
   WHERE u.squadron_id IS NOT NULL
     AND NOT EXISTS (
       SELECT 1 FROM org_unit_membership m
        WHERE m.user_id = u.id
          AND m.kind = 'SQUADRON'
          AND m.org_unit_id = u.squadron_id);

  -- Every user.is_logistician=TRUE has a corresponding membership flag.
  SELECT COUNT(*) FROM app_user u
   WHERE u.is_logistician = TRUE
     AND NOT EXISTS (
       SELECT 1 FROM org_unit_membership m
        WHERE m.user_id = u.id
          AND m.kind = 'SQUADRON'
          AND m.is_logistician = TRUE);

  -- Same for is_mission_manager.
  SELECT COUNT(*) FROM app_user u
   WHERE u.is_mission_manager = TRUE
     AND NOT EXISTS (
       SELECT 1 FROM org_unit_membership m
        WHERE m.user_id = u.id
          AND m.kind = 'SQUADRON'
          AND m.is_mission_manager = TRUE);
  ```
  Each query must return `0`. If any returns > 0:
  - V99 backfill didn't catch every row (rare, but possible if rows were
    inserted between V99 running and PR-1's lifecycle hook taking effect)
    or R6.e dual-write missed a write path. Investigate the gap first;
    don't push through with broken data.

### 3.2 Retarget the base

After PR-2 has merged + deployed + soaked:

```bash
gh pr edit 228 --base main
git fetch origin
git checkout consolidate/spezialkommando-cleanup
git rebase origin/main
./gradlew :backend:test :frontend:test
git push --force-with-lease origin consolidate/spezialkommando-cleanup
```

Confirm PR-3's diff shows only 8 commits, not 30+.

### 3.3 What it does

- **V102:** `ALTER TABLE ... ALTER COLUMN owning_org_unit_id SET NOT NULL`
  on every staffel-scoped aggregate; `DROP NOT NULL` on the legacy
  `owning_squadron_id` columns. This **unlocks SK ownership** — the
  rejection branch in `OwnerScopeService.resolveSquadronForPickerOutput`
  ("Spezialkommando ownership not yet supported") is no longer reachable.
- **V103:** Drops the legacy `owning_squadron_id` / `creating_squadron_id` /
  `requesting_squadron_id` columns on every aggregate + the V91/V93 indexes
  that referenced them. After this, the only owner column on each aggregate
  is `owning_org_unit_id`.
- **V104:** Drops `app_user.squadron_id` (column + FK + index) and
  `app_user.is_logistician` / `app_user.is_mission_manager`. The
  per-membership row is the sole authority.
- **V105:** Retargets the 3 FKs that still reference `squadron(id)`
  (`promotion_topic.owning_squadron_id`, `mission_participant.squadron_id`,
  `job_order_handover.executing_squadron_id`) to `org_unit(id)`. Drops the
  V100 sync trigger + function. Drops the `squadron` table itself.

After this PR ships, **the legacy `squadron` table no longer exists**, and
`org_unit_membership` is the sole source of truth for "which Staffel does
this user belong to" and the contextual role flags.

### 3.4 Pre-merge checks

- [ ] §3.1 prerequisites all green.
- [ ] PR-3 retargeted to `main`.
- [ ] CI is green on PR #228.
- [ ] Local re-verify: `./gradlew :backend:checkstyleMain :backend:spotbugsMain
  :frontend:checkstyleMain :frontend:spotbugsMain` BUILD SUCCESSFUL. **Tests
  may report 2 known pre-existing failures** (`HangarIntegrationTest`,
  `RefineryOrderTest`) — verify they are exactly the two acknowledged in PR-3
  body and not new regressions.
- [ ] **Backup verification command** runs and succeeds:
  ```bash
  pg_restore -l <backup-file> > /tmp/backup.toc  # list contents
  grep -c "TABLE.*public.squadron\|TABLE.*public.org_unit\|TABLE.*public.org_unit_membership" /tmp/backup.toc
  # Expected output: 3
  ```

### 3.5 Merge + deploy

- **Take one more backup** immediately before merging. Time-stamped, kept
  separate from your daily backup rotation.
- Merge PR #228.
- Deploy with **maintenance mode enabled** (display a banner / 503 page to
  end users while migrations run).
- Backend startup will run V102 → V103 → V104 → V105. Total expected time:
  - **V102** NOT NULL changes: a few seconds on small tables, minute-scale
    on big tables (millions of rows).
  - **V103** column drops: fast (PostgreSQL stores columns as catalog
    metadata; DROP COLUMN is a catalog update without rewriting the table).
  - **V104** same as V103.
  - **V105** FK retargets + trigger drop + table drop: a few seconds.
- **If any migration fails**: STOP. The migration that failed will have
  rolled back its own transaction. Check the error in `flyway_schema_history`
  (last row, `success = false`). Decide:
  - If recoverable (e.g. transient lock timeout): re-run by restarting the
    app. Flyway will retry the failed migration.
  - If unrecoverable (data integrity issue caught by the migration):
    restore from backup. Investigate offline.

### 3.6 Post-deploy verification (mandatory)

| Step | Expected |
| -- | -- |
| `SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'squadron'` | `0` (table dropped). |
| `SELECT proname FROM pg_proc WHERE proname = 'sync_org_unit_to_squadron'` | `0` (trigger function dropped). |
| `SELECT proname FROM pg_proc WHERE proname = 'guard_promotion_topic_owner_kind'` | `1` (V101 guard stays). |
| `SELECT column_name FROM information_schema.columns WHERE table_name='mission' AND column_name='owning_squadron_id'` | empty (V103 dropped it). |
| `SELECT column_name FROM information_schema.columns WHERE table_name='app_user' AND column_name IN ('squadron_id', 'is_logistician', 'is_mission_manager')` | empty (V104 dropped them). |
| `SELECT confrelid::regclass FROM pg_constraint WHERE conname = 'fk_promotion_topic_owning_squadron'` | `org_unit` (V105 retargeted). |
| App `/actuator/health` | `UP`. |
| Login as existing Staffel-only user | JWT-resolved roles match expected (verify with a logistician-only action). |
| Mission create | Picker hidden for single-membership user; defaults to the user's Staffel via membership row. |
| Inventory transfer to an SK-only user (if one exists after R6.e backfill) | Succeeds; new inventory row has `owning_org_unit_id` = the SK. |
| Hangar add-ship | Stamps on the picker'd OrgUnit (or auto-stamps the user's single Staffel). |
| Admin assigns user A as member of SK X | `org_unit_membership` row created; user A's next login carries the SK in their membership union. |

### 3.7 Soak window — n/a

This is the terminal release. No further PRs in this rollout. Standard
post-release monitoring applies.

### 3.8 Rollback (PR-3 — IRREVERSIBLE without backup)

If the post-deploy verification fails or production exhibits regressions:

1. **You cannot revert via Git alone.** The schema migrations dropped data
   that isn't in Git.
2. **Restore the DB from the backup taken in §3.5.** This is a full restore;
   coordinate with whoever runs the DB host.
3. After restore, revert the PR-3 merge commit in Git and re-deploy.
4. The app will boot against the restored schema (with the `squadron` table
   present, `owning_squadron_id` columns present, `app_user.squadron_id`
   present). Flyway will see the restored `flyway_schema_history` and pick
   up where it left off — V102+ will be absent so it won't try to re-apply.

The restoration window is dictated by your DB backup-restore time (likely
30–60 minutes for a multi-gigabyte database).

---

## 4. Post-rollout cleanup (after PR-3 deployed and stable)

After PR-3 has been in production for one release cycle without incident:

1. **Close issue [#214](https://github.com/krt-iri/basetool/issues/214)** ("Unterstützung für Spezialkommandos").
2. **Move all items in [Project 1](https://github.com/orgs/krt-iri/projects/1) to "Done"** status.
3. **Tag the release** with a semver bump:
   ```bash
   git tag -a v0.X.Y -m "Spezialkommando rollout complete (V97-V105)"
   git push origin v0.X.Y
   ```
4. **Update [CHANGELOG.md](CHANGELOG.md)** — move the `[Unreleased]` section
   into a `[v0.X.Y] — YYYY-MM-DD` block.
5. **Delete the old per-release branches** after a 30-day grace period:
   ```bash
   # List candidates
   git branch -r --no-merged origin/main | grep claude/spezialkommando

   # Delete each (use --force-with-lease equivalent: -d not -D in case there's
   # any unmerged commit you missed)
   git push origin --delete claude/spezialkommando-r1
   # ... etc
   ```
6. **Delete the three consolidation branches** after the same grace period:
   ```bash
   git push origin --delete consolidate/spezialkommando-foundation
   git push origin --delete consolidate/spezialkommando-identity
   git push origin --delete consolidate/spezialkommando-cleanup
   ```
7. **Archive `R8_DESTRUCTIVE_ROADMAP.md` and `SK_ROLLOUT_RUNBOOK.md`**: they
   describe completed work. Move under `docs/archive/` or delete if you
   prefer a cleaner repo root.

---

## 5. Common pitfalls

- **Don't merge two stages back-to-back.** Each stage's soak window catches
  regressions that the integration tests can't simulate (real user load,
  long-running transactions, cache invalidation patterns).
- **Don't skip the backup before PR-3.** V103–V105 are irreversible. The
  "I'll roll back via Git" reflex does not work — Git doesn't carry the
  dropped data.
- **Don't merge PR-3 before PR-2 has soaked.** R8 + R9 assume the
  membership row is authoritative (R6.d JWT converter) and that the dual-
  write is in lockstep (R6.e). Without those, V104 (dropping `app_user`
  legacy columns) could orphan users from their roles.
- **Don't use `--no-verify` on commits.** The DCO check is mandatory on PR
  branches — bypassing it locally just means CI catches it later.
- **Don't change V-numbers after the PR is opened.** Reviewers' inline
  comments reference specific file paths; renumbering breaks those refs and
  forces a re-review.
- **Don't run the migrations against the production `.env`** for staging
  purposes. Use `.env.test` with throwaway credentials, per the project's
  hard rule (CLAUDE.md "Testing" section, memory
  `feedback_env_test_isolation`).
- **Don't forget the active-context header alias window.** Browsers may
  cache the old `X-Active-Squadron-Id` header through PR-1's soak. Both
  names are honored until PR-3 drops the legacy one (which it does
  implicitly via the user-table column drop in V104 — the session-attribute
  alias gets cleaned up via deploy-time session invalidation if you have
  one configured; otherwise users see one extra Keycloak round-trip after
  PR-3, harmless).
- **Don't be surprised by the active-context switcher appearing for some
  non-admin users after PR-1.** That's by design — any user with >1
  membership sees it. Before PR-1, no non-admin had >1 membership (the
  schema didn't support it), so the switcher was admin-only de facto.

---

## 6. Emergency contacts and references

- **Primary maintainer:** Lucas Greuloch (`lucas.greuloch@pm.me`).
- **Production deploy guide:** [`docs/deployment.md`](docs/deployment.md).
- **Migration conventions:** [`backend/src/main/resources/db/migration/README.md`](backend/src/main/resources/db/migration/README.md).
- **Design doc:** [`SPEZIALKOMMANDO_PLAN.md`](SPEZIALKOMMANDO_PLAN.md).
- **Destructive-ops roadmap (legacy reference):** [`R8_DESTRUCTIVE_ROADMAP.md`](R8_DESTRUCTIVE_ROADMAP.md).
- **Logs in prod:** `logs/backend.json`, `logs/frontend.json` (Logstash
  JSON encoder per `application-prod.yml`).
- **Health endpoints:** `/actuator/health` on both backend and frontend.

---

End of runbook. Update this document with lessons learned after each
deploy.
