# Flyway Migration Conventions

This directory holds every schema change the backend has ever shipped. Flyway
applies the files in lexical order by version on every Spring Boot start in the
`dev` and `prod` profiles. Hibernate's `ddl-auto` is **`validate`** in those
profiles, so a schema that doesn't match the JPA entities fails the app
boot — there is no "auto-fix" path in production.

Read this file before you add a migration. It encodes promises we made to
ourselves after each production incident; the conventions below exist because
something broke once.

## Migration timeline — feature releases

- **V80–V93** — Multi-Squadron-Umbau (see [`MULTI_SQUADRON_PLAN.md`](../../../../../../../../MULTI_SQUADRON_PLAN.md)).
  Introduced `owning_squadron_id` on every staffel-scoped aggregate; tightened
  the NOT NULL constraint in V89; dropped the legacy `job_order.squadron`
  VARCHAR in V90.
- **V94–V96** — Main-line additions unrelated to the Spezialkommando work:
  - **V94** — `add_is_manual_entry_to_material` (admin-created manual materials).
  - **V95** — `backfill_material_quantity_type`.
  - **V96** — `add_mission_participant_user_unique_index` (DB-backstop against
    duplicate Einsatz-Anmeldungen).
- **V97–V101** — Spezialkommando-Erweiterung (see `SPEZIALKOMMANDO_PLAN.md`).
  Introduces the `org_unit` parent table with a `kind` discriminator so SKs
  can coexist with Staffel as a second tenant kind. The releases land in
  stages:
  - **V97** — create `org_unit` with `kind IN ('SQUADRON','SPECIAL_COMMAND')`,
    copy every existing `squadron` row in as `kind='SQUADRON'`, add the
    promotion-only-for-Squadron CHECK.
  - **V98** — create `org_unit_membership` (composite PK, denormalised `kind`
    column kept in sync by a trigger, partial unique index `one Staffel per
    user`, `is_lead` CHECK that pins the Lead role to SK rows), backfill one
    Staffel membership per `app_user.squadron_id`.
  - **V99** — add nullable `owning_org_unit_id` columns + FKs + indexes on
    every staffel-scoped aggregate (`mission`, `operation`, `ship`,
    `inventory_item`, `refinery_order`) and `job_order` (`creating_org_unit_id`,
    `requesting_org_unit_id`); copy from the legacy columns. `promotion_topic`
    is intentionally **not** touched — promotion data may only be owned by
    Squadron rows per `SPEZIALKOMMANDO_PLAN.md` §3.3.
  - **V100** — one-way sync trigger that mirrors INSERT/UPDATE/DELETE on
    `org_unit` (for `kind='SQUADRON'` rows) into the legacy `squadron` table.
    Lets the application write through `org_unit` exclusively while the
    legacy FK constraints (still pointing at `squadron(id)`) keep resolving.
  - **V101** — promotion-topic SK-reject trigger (`guard_promotion_topic_owner_kind`)
    that refuses any INSERT/UPDATE of `promotion_topic.owning_squadron_id`
    pointing at a non-SQUADRON org_unit. DB-level defence-in-depth on top of
    the Java-typed Squadron field and ArchUnit rule §8.2.
- **V102+ (destructive cleanup, deferred)** — tighten `owning_org_unit_id` to
  NOT NULL on every aggregate, drop the legacy `owning_squadron_id` / job_order
  squadron FKs, drop `app_user.squadron_id` / `is_logistician` /
  `is_mission_manager` (the per-membership row is authoritative since R6.d),
  drop the legacy `squadron` table once V100's mirror is no longer needed.
  Lands only after R6.e has soaked one full release cycle in prod.

## Hard rules

1. **One file per change, `V<n>__<snake_case_description>.sql`.** Pick the next
   integer; never reuse a number that's already in `main`. Two developers
   racing to `V73` should rebase, not double-pick. Flyway treats the version
   tuple as an immutable identifier — once `V73__foo.sql` has run anywhere
   (a teammate's laptop, CI, staging) the file content **must not** change.
2. **One logical change per migration.** Splitting a "rename + backfill + add
   constraint" sequence across three migrations is fine; bundling unrelated
   changes ("add column X to table Y, drop column Z from table W") into one
   file is not.
3. **No `ddl-auto=update`. Never.** Schema changes go here, in a Flyway file,
   even if they're "obvious". `application*.yml` is set to `validate`
   specifically so a missing migration breaks the app boot loudly instead of
   silently drifting at runtime.
4. **Up-only.** We do not maintain undo scripts. A migration that turns out to
   be wrong is corrected by a *new* `V<n+1>__<description>.sql` that reverses
   the bad change. Rolling a database back across multiple environments is
   strictly more expensive than rolling forward, every time.
5. **PostgreSQL syntax only.** This project does not target any other database
   in production. H2-specific dialect in tests is handled separately by the
   test profile (which currently skips Flyway entirely; see the section
   "Tests" below).

## Destructive operations (`DROP TABLE` / `DROP COLUMN`)

Dropping things in a live database is the most expensive class of migration.
Rules:

* **Two-phase drop.** Never delete a column or table in the same migration that
  removes the last reader. The first migration stops *writing* the column and
  removes it from the entity; only the *next* migration, ideally one release
  later, runs the `DROP COLUMN`. The grace period gives time to roll back the
  app deployment without losing the column.
  - Phase 1 (`V<n>__stop_writing_old_column.sql`): drop NOT NULL on the
    column, drop any constraints referencing it, leave the data in place.
    Update the entity at the same time so it no longer touches the column.
  - Phase 2 (`V<n+k>__drop_old_column.sql`): the actual `ALTER TABLE ... DROP
    COLUMN`. Land this in a *separate* release, after at least one production
    deploy with phase 1.
* **Drop-then-add in one file is allowed only on tables that did not yet ship
  to production**, i.e. for migrations younger than the most recently
  deployed version. Use sparingly and only during the very first iteration of
  a feature.
* **`DROP CONSTRAINT IF EXISTS`** before dropping the column it references, so
  the migration is robust against partially-applied state on developer
  databases. See [`V21__update_refinery_good.sql`](V21__update_refinery_good.sql)
  for the pattern.

If you are about to drop something and you are not 100 % sure no other code
path reads it, search the whole repository for the column / table name
(`Grep` across `backend/`, `frontend/`, `keycloak-theme/`, `realm-export.json`)
before merging.

## Data migrations

Schema-only migrations (pure `CREATE TABLE`/`ALTER TABLE`) are easy. Data
migrations are where things go subtly wrong.

* **Backfill in SQL inside the migration file.** Use `UPDATE ... WHERE ...`
  statements between the `ALTER TABLE ADD COLUMN` and the
  `ALTER TABLE ALTER COLUMN ... SET NOT NULL`. See
  [`V72__add_role_code.sql`](V72__add_role_code.sql) — it shows the canonical
  add-column → seed-known-rows → derive-the-rest → tighten-NOT-NULL sequence.
* **Don't backfill from Java.** A `DataInitializer`-style backfill happens
  *after* Spring's `EntityManagerFactory` validates the schema; if the column
  is `NOT NULL` at that point, validation has already failed. Putting the
  backfill in SQL inside the migration makes the constraint-tightening step
  atomic with the data fix.
* **Idempotent default values.** When a backfill sets a default ("everyone
  who joined before 2025 gets `is_active = true`"), the SQL must be
  idempotent so re-running on a partially-applied DB does not change the
  result. Prefer `WHERE column IS NULL` clauses, not `UPDATE ... SET ... ;`
  unconditional rewrites.
* **No side-effects to other tables.** A migration named
  `V73__add_join_date_to_user.sql` must not touch the `mission` table; if a
  cross-table change is needed, that is a separate migration in the same PR.

## Performance / locking

* `ALTER TABLE` on PostgreSQL acquires an `ACCESS EXCLUSIVE` lock for the
  duration of the statement. For tables with significant row count
  (`inventory_item`, `mission`, `job_order`, `material`) prefer
  `ADD COLUMN <name> <type>` *without* a default — that's an `O(1)` metadata
  update. If a default is required, ship it in two phases (add as NULL,
  backfill, set default + NOT NULL) to keep each lock short.
* `CREATE INDEX CONCURRENTLY` is **not** available inside a migration because
  Flyway runs each file in a transaction by default. Long-running index
  creation has to either:
  - run outside Flyway (manual DBA step, documented in the PR), or
  - be added with `-- ${flyway:noTransaction}` at the top of the file plus an
    explicit `CREATE INDEX CONCURRENTLY` statement.
  Currently the codebase does not need this; if you do, see
  [`db.DatabaseIndexMigrationTest`](../../../../test/java/de/greluc/krt/iri/basetool/backend/db/DatabaseIndexMigrationTest.java)
  for how the existing test enforces what indexes ship.

## Tests

The test profile (`application-test.yml`) currently runs against H2 and sets
`flyway.enabled: false`, so Flyway scripts are **not** executed by the standard
test suite. Moving tests onto Testcontainers + Postgres so migrations actually
run there is an open follow-up. Until that lands:

* Don't assume `./gradlew test` validated your migration. Run the dev
  Compose stack at least once before merging:
  ```bash
  docker compose --profile dev up -d db-backend-dev keycloak-dev redis-dev
  ./gradlew :backend:bootRun
  ```
  The first boot after your migration must succeed without `ddl-auto=validate`
  complaints.
* If you change the schema in a way that affects Hibernate's entity mapping
  (rename a column, change a type, add a constraint), update the entity in
  the **same** commit. A migration that ships without the entity change will
  break every `bootRun` on the next pull.
* If you add a column that is used by `DataInitializer`, also extend
  `BackendApplicationTests` (or a more focused integration test) to verify
  the seeded state.

## File header convention

Every migration file should open with a short SQL comment explaining *why*
the change is happening, not just what. Future-you reading `V42__...sql`
in five years will not remember the bug or feature request that motivated
it. Example:

```sql
-- Stable, machine-readable identifier for roles. The display name (`name`) can
-- be renamed by an admin without changing the role's identity. `code` is what
-- the DataInitializer matches against on startup so a renamed role is no
-- longer silently re-created with default permissions on the next boot.
ALTER TABLE role ADD COLUMN code VARCHAR(64);
```

A reader should be able to grep `git log -- V42__*` and find the PR; the
comment should answer the obvious "why now?" question before they need to.

## Checklist before merging a migration

- [ ] Filename is `V<next-unused-integer>__<snake_case>.sql`.
- [ ] Top-of-file comment explains *why*, not just what.
- [ ] No `DROP TABLE` / `DROP COLUMN` on data that's already in production
      without a phase-1 stop-writing predecessor in an earlier release.
- [ ] Backfill (if any) is idempotent and inside the same migration file.
- [ ] The matching JPA entity was updated in the same commit.
- [ ] `./gradlew :backend:bootRun` against the dev Postgres started cleanly.
- [ ] If indexes were added, [`DatabaseIndexMigrationTest`](../../../../test/java/de/greluc/krt/iri/basetool/backend/db/DatabaseIndexMigrationTest.java)
      knows about them.
- [ ] The change is mentioned in `CHANGELOG.md` under the right `### Added`
      / `### Changed` / `### Migration` heading.
