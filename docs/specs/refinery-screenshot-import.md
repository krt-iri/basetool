> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-10.
> **Owner area:** REFINERY · **Related ADRs:** none yet (the epic's architecture ADRs land with
> their phases, see [`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md) §8)

# Refinery screenshot import

## Context & goal

Living requirements for the refinery screenshot import (epic #439): importing a
`RefineryExtract` JSON produced from an in-game refinery screen and matching it onto master
data. The forward plan lives in
[`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md); each shipped
phase mints its requirements here. **`REQ-REFINERY-001` – `REQ-REFINERY-009` are reserved
for the epic's phase requirements** and are not yet minted — the epic has not started.

The first minted requirement below hardens shared infrastructure the import depends on: the
`material_external_alias` table, which both the SC Wiki commodity sync and the import's
material-matching chain consult through a **case-insensitive** lookup.

## Requirements

### REQ-REFINERY-010 — Material-alias uniqueness is case-insensitive

At most one `material_external_alias` row may exist per
`(source_system, LOWER(external_name))`. The uniqueness rule must match the resolution
lookup (`findBySourceSystemAndExternalNameIgnoreCase`), which folds case because external
systems drift casing across patch versions (the refinery screen shows `"STILERON (ORE)"`
where the Wiki writes `"Stileron (Ore)"`).

*Why:* the original V108 constraint was case-sensitive while the lookup was not. Two rows
differing only in case could legally coexist, making the `Optional`-returning lookup throw
`IncorrectResultSizeDataAccessException` → HTTP 500 on **every** import/sync touching that
name. V146 de-duplicated existing case-variant rows (oldest row per group survives) and
replaced the constraint with the functional unique index
`uq_material_external_alias_source_lower_name`.

**Acceptance**

- [x] The DB rejects a second alias whose `(source_system, external_name)` differs from an
  existing row only in case (V146 unique index on `(source_system, LOWER(external_name))`).
- [x] `MaterialExternalAliasService.create` and `.update` detect a case-insensitive
  duplicate pre-emptively and raise `DuplicateEntityException` → HTTP 409 (clean conflict
  instead of a generic DB error); recasing a row's *own* name remains allowed.
- [x] `resolveMaterialByAlias` can never observe two candidate rows, so the
  `IncorrectResultSizeDataAccessException` failure mode is structurally impossible.

**Enforced by:** `MaterialExternalAliasServiceTest`, `DatabaseIndexMigrationTest` ·
**Code:** `MaterialExternalAliasService`, `MaterialExternalAliasRepository`,
`V146__make_material_alias_uniqueness_case_insensitive.sql` · **Issues:** epic #439

## Out of scope

- The import pipeline itself (extract DTOs, draft endpoint, review UI, persistence) — the
  forward plan in [`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md)
  governs it until each phase ships and mints `REQ-REFINERY-001+` here.
- `blueprint_external_alias` — the blueprint import keeps its own alias table and matching
  rules, specced in [`blueprint-import-name-matching.md`](blueprint-import-name-matching.md).

## Open questions

None.
