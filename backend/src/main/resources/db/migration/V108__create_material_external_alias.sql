-- =====================================================================
-- V108 - SC Wiki sync R1 (foundation): material_external_alias table + seed
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.2 introduces a curated cross-reference
-- table that lets admins (and the seed below) map external commodity
-- names from UEX / SC Wiki onto an existing local `material` row, for
-- cases where the auto-matcher (exact name, canonical-form, UUID) can't
-- safely derive the link. The R3 Wiki commodity sync's resolution chain
-- consults this table as step 2 (after UUID match) before falling back
-- to name heuristics.
--
-- Seed (§4.1 fuzzy + §4.2 manual, both verified on 2026-05-27 against
-- the live Wiki + UEX detail endpoints — see /tmp/sc-sync-research/
-- detail-wiki/*.json):
--   1. Raw Silicon            → Silicon (Raw)        (fuzzy, §4.1)
--   2. Stileron (Ore)         → Stileron (Raw)       (fuzzy, §4.1)
--   3. Raw Ouratite           → Ouratite (Raw)       (fuzzy, §4.1)
--   4. Hephaestanite (R)      → Hephaestanite (Raw)  (fuzzy, §4.1)
--   5. Lastaprene             → Lastaphrene          (UEX typo, §4.2)
--   6. Lunes (Spiral Fruit)   → Lunes                (taxonomy, §4.2)
--
-- §4.2.1 explicitly EXCLUDES the Construction-* triplet (Pieces / Rubble
-- / Salvage) and the wiki "Combat Supplies" entry — those require
-- in-game grade verification. An admin adds them via /admin/material-
-- aliases after testing.
--
-- The seed uses INSERT ... SELECT ... WHERE EXISTS so a fresh DB (where
-- the UEX commodity sync has not run yet and the target `material` rows
-- don't exist) inserts zero aliases without violating the FK. On the
-- next sync run, the admin can re-add the missing aliases manually --
-- or simply re-run a small SQL the runbook documents. ON CONFLICT
-- guards against re-seeding the same (source_system, external_name) on
-- a re-deploy where the column existed but the row was deleted.
--
-- Rollback: DROP TABLE material_external_alias. No FK from other tables
-- references this table; it's a leaf catalogue.

CREATE TABLE IF NOT EXISTS material_external_alias (
    id            UUID                     PRIMARY KEY,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    material_id   UUID                     NOT NULL REFERENCES material(id),
    source_system VARCHAR(32)              NOT NULL,
    external_name VARCHAR(255)             NOT NULL,
    external_key  VARCHAR(255),
    external_uuid UUID,
    external_code VARCHAR(64),
    note          TEXT,
    created_by    VARCHAR(255),
    CONSTRAINT chk_material_external_alias_source_system
        CHECK (source_system IN ('UEX', 'SCWIKI')),
    CONSTRAINT uk_material_external_alias_source_external_name
        UNIQUE (source_system, external_name)
);

CREATE INDEX IF NOT EXISTS idx_material_external_alias_material
    ON material_external_alias(material_id);

-- ---------------------------------------------------------------------
-- Seed aliases — only for rows whose target UEX material already exists.
-- See header comment for the verification provenance.
-- ---------------------------------------------------------------------
INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Raw Silicon',
       'V108 seed (§4.1 fuzzy): Wiki "Raw Silicon" maps to UEX "Silicon (Raw)" — both mineable, density 2.34 g/cc, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Silicon (Raw)'
ON CONFLICT (source_system, external_name) DO NOTHING;

INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Stileron (Ore)',
       'V108 seed (§4.1 fuzzy): Wiki "Stileron (Ore)" maps to UEX "Stileron (Raw)" — same Man-made raw form, density 4.75 g/cc, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Stileron (Raw)'
ON CONFLICT (source_system, external_name) DO NOTHING;

INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Raw Ouratite',
       'V108 seed (§4.1 fuzzy): Wiki "Raw Ouratite" maps to UEX "Ouratite (Raw)" — density 1.10 g/cc, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Ouratite (Raw)'
ON CONFLICT (source_system, external_name) DO NOTHING;

INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Hephaestanite (R)',
       'V108 seed (§4.1 fuzzy): Wiki "Hephaestanite (R)" maps to UEX "Hephaestanite (Raw)" — density 3.20 g/cc, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Hephaestanite (Raw)'
ON CONFLICT (source_system, external_name) DO NOTHING;

INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Lastaprene',
       'V108 seed (§4.2 manual): Wiki "Lastaprene" maps to UEX "Lastaphrene" — UEX carries the typo, both density 1, Man-made, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Lastaphrene'
ON CONFLICT (source_system, external_name) DO NOTHING;

INSERT INTO material_external_alias
    (id, material_id, source_system, external_name, note, created_by)
SELECT gen_random_uuid(), m.id, 'SCWIKI', 'Lunes (Spiral Fruit)',
       'V108 seed (§4.2 manual): Wiki "Lunes (Spiral Fruit)" maps to UEX "Lunes" — Wiki keeps the parenthetical taxonomic suffix, verified 2026-05-27.',
       'system'
  FROM material m
 WHERE m.name = 'Lunes'
ON CONFLICT (source_system, external_name) DO NOTHING;
