-- =====================================================================
-- V146 - case-insensitive uniqueness for material_external_alias
-- =====================================================================
-- Why: the V108 UNIQUE constraint on (source_system, external_name) is
-- case-SENSITIVE, but the resolution lookup consumed by the SC Wiki
-- commodity sync and the refinery screenshot import
-- (findBySourceSystemAndExternalNameIgnoreCase) folds case. Two rows
-- differing only in case (e.g. 'STILERON (ORE)' and 'Stileron (Ore)'
-- under the same source) could therefore legally coexist and made the
-- Optional-returning lookup throw IncorrectResultSizeDataAccessException
-- -> HTTP 500 on every import/sync touching that name. See
-- REQ-REFINERY-010 (docs/specs/refinery-screenshot-import.md).
--
-- Step 1 de-duplicates existing case-variant rows: per
-- (source_system, LOWER(external_name)) group the oldest row survives
-- (created_at, ties broken by id) so the statement is deterministic and
-- idempotent. Step 2 swaps the case-sensitive constraint for a unique
-- index on the case-folded name. The service layer performs the same
-- check pre-emptively (clean HTTP 409); this index is the DB-level
-- catch-all.

DELETE FROM material_external_alias dup
 USING material_external_alias keeper
 WHERE dup.source_system = keeper.source_system
   AND LOWER(dup.external_name) = LOWER(keeper.external_name)
   AND dup.id <> keeper.id
   AND (keeper.created_at < dup.created_at
        OR (keeper.created_at = dup.created_at AND keeper.id < dup.id));

ALTER TABLE material_external_alias
    DROP CONSTRAINT IF EXISTS uk_material_external_alias_source_external_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_material_external_alias_source_lower_name
    ON material_external_alias (source_system, LOWER(external_name));
