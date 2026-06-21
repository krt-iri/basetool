-- =====================================================================
-- V176 - case-insensitive uniqueness for blueprint_external_alias
-- =====================================================================
-- Why: the V127 UNIQUE constraint on (source_system, external_name) is
-- case-SENSITIVE, but the personal-blueprint import resolution lookup
-- (findBySourceSystemAndExternalNameIgnoreCase) folds case. External
-- systems drift casing across patch versions, and the scmdb.net tag match
-- (REQ-INV-019) now routes many name-mismatching entries through the
-- alias-learning path, so two rows differing only in case could legally
-- coexist and made the Optional-returning lookup throw
-- IncorrectResultSizeDataAccessException -> HTTP 500 on every import
-- touching that name. Mirrors the material_external_alias fix (V146) and
-- REQ-INV-020 (docs/specs/blueprint-import-name-matching.md).
--
-- Step 1 de-duplicates existing case-variant rows: per
-- (source_system, LOWER(external_name)) group the oldest row survives
-- (created_at, ties broken by id) so the statement is deterministic and
-- idempotent. Step 2 swaps the case-sensitive constraint for a unique
-- index on the case-folded name. The service layer performs the same
-- check pre-emptively; this index is the DB-level catch-all.

DELETE FROM blueprint_external_alias dup
 USING blueprint_external_alias keeper
 WHERE dup.source_system = keeper.source_system
   AND LOWER(dup.external_name) = LOWER(keeper.external_name)
   AND dup.id <> keeper.id
   AND (keeper.created_at < dup.created_at
        OR (keeper.created_at = dup.created_at AND keeper.id < dup.id));

ALTER TABLE blueprint_external_alias
    DROP CONSTRAINT IF EXISTS uk_blueprint_external_alias_source_external_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_blueprint_external_alias_source_lower_name
    ON blueprint_external_alias (source_system, LOWER(external_name));
