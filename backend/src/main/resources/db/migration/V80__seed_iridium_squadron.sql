-- =====================================================================
-- V80 - Seed the IRIDIUM squadron with a canonical UUID
-- =====================================================================
-- Why: multi-tenancy makes squadron the canonical tenant boundary. All
-- squadron-scoped data backfilled in V81-V83 must point at a stable
-- IRIDIUM UUID, and the application code carries Squadron.IRIDIUM_ID as
-- a constant. Before this migration the IRIDIUM squadron was created by
-- DataInitializer with a random UUID, which prevents deterministic
-- backfills and makes test setup awkward.
--
-- Three cases are handled idempotently:
--   1. Fresh DB / no IRIDIUM row    -> insert with canonical UUID.
--   2. IRIDIUM exists at canonical  -> no-op.
--   3. IRIDIUM exists at non-canonical UUID -> rewrite the UUID and
--      cascade to the single known FK (mission_participant.squadron_id).
--
-- If a different squadron already squats on the canonical UUID we
-- raise loudly: that is operator-level cleanup, not a silent overwrite.

DO $$
DECLARE
    canonical_iri_id UUID := '00000000-0000-0000-0000-000000000001';
    existing_iri_id  UUID;
    canonical_occupant_shorthand VARCHAR;
BEGIN
    SELECT id INTO existing_iri_id
      FROM squadron
     WHERE shorthand = 'IRI'
     LIMIT 1;

    SELECT shorthand INTO canonical_occupant_shorthand
      FROM squadron
     WHERE id = canonical_iri_id
     LIMIT 1;

    IF existing_iri_id IS NULL THEN
        IF canonical_occupant_shorthand IS NOT NULL THEN
            RAISE EXCEPTION
                'Squadron at canonical IRIDIUM UUID has unexpected shorthand %, aborting V80.',
                canonical_occupant_shorthand;
        END IF;

        INSERT INTO squadron (id, version, created_at, updated_at, name, shorthand, description, active)
        VALUES (
            canonical_iri_id,
            0,
            NOW(),
            NOW(),
            'IRIDIUM',
            'IRI',
            'The main squadron.',
            TRUE
        );

    ELSIF existing_iri_id <> canonical_iri_id THEN
        IF canonical_occupant_shorthand IS NOT NULL THEN
            RAISE EXCEPTION
                'Squadron at canonical IRIDIUM UUID has unexpected shorthand %, aborting V80.',
                canonical_occupant_shorthand;
        END IF;

        UPDATE mission_participant
           SET squadron_id = canonical_iri_id
         WHERE squadron_id = existing_iri_id;

        UPDATE squadron
           SET id = canonical_iri_id
         WHERE id = existing_iri_id;
    END IF;
END $$;
