-- =====================================================================
-- V146 - Refinery screenshot import (#434, epic #439): REFINERY_SCREEN
--        alias source for material_external_alias
-- =====================================================================
-- Why: the import endpoint resolves verbatim SC refinement-screen names
-- ("STILERON (ORE)", game-UI-truncated reads) against the local material
-- catalogue. Stage 2 of the matching chain consults the curated V108
-- alias table under a dedicated source bucket, so admins can fix a
-- recurring mis-read at /admin/material-aliases with one click instead
-- of a code change. The V108 CHECK constraint only allowed UEX/SCWIKI;
-- this widens it for the new MaterialExternalAliasSource.REFINERY_SCREEN
-- enum value (entity updated in the same commit).
--
-- No rows are seeded: refinery-screen aliases come from Phase 0 golden-
-- set verification (#433) via admin curation, mirroring the V108 policy
-- of only shipping verified aliases.
--
-- Rollback: re-create the constraint with the old two-value list after
-- deleting any REFINERY_SCREEN rows.

ALTER TABLE material_external_alias
    DROP CONSTRAINT IF EXISTS chk_material_external_alias_source_system;

ALTER TABLE material_external_alias
    ADD CONSTRAINT chk_material_external_alias_source_system
        CHECK (source_system IN ('UEX', 'SCWIKI', 'REFINERY_SCREEN'));
