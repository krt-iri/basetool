-- =====================================================================
-- V97 - Spezialkommando R2.b step 1: one-way sync org_unit -> squadron
-- =====================================================================
-- Why: R2.b refactors the Squadron JPA entity to extend the OrgUnit
-- abstract superclass and re-points its mapping at the `org_unit` table
-- (single-table inheritance, kind = 'SQUADRON'). After that change every
-- Squadron INSERT/UPDATE/DELETE issued by the application goes through
-- the org_unit table, NOT the legacy `squadron` table.
--
-- The legacy `squadron` table cannot just stop being maintained, though:
--   * `app_user.squadron_id`, `mission_participant.squadron_id` and every
--     aggregate's `owning_squadron_id` / `creating_squadron_id` /
--     `requesting_squadron_id` column is still a foreign key constraint
--     to `squadron(id)` (V81-V83) - inserts into `app_user` referencing
--     a Squadron that exists only in `org_unit` would fail the FK check.
--   * R1 rollback is "drop org_unit + org_unit_membership and you are
--     back on the pre-R1 schema" only if `squadron` is still the
--     authoritative copy at the moment of rollback. If the application
--     wrote to org_unit for a few hours before someone hit rollback,
--     those writes would vanish.
--
-- Solution: a one-way trigger that mirrors every change to the
-- SQUADRON-kind rows of `org_unit` into `squadron`. The application
-- knows about `org_unit` only; `squadron` is a passive replica
-- maintained by the database.
--
-- Direction is INTENTIONALLY one-way (org_unit -> squadron, never the
-- reverse). Any direct SQL write to `squadron` after V97 is a bug:
-- the application now writes to org_unit, the seeder (DataInitializer)
-- now writes to org_unit via the JPA layer, and nothing else should be
-- touching either table at runtime. A bidirectional trigger would
-- create write loops; defensive engineering prefers the simpler
-- one-way path.
--
-- Idempotency: CREATE OR REPLACE FUNCTION + DROP TRIGGER IF EXISTS so
-- the migration replays cleanly on a developer DB that may have an
-- earlier draft of the trigger.
--
-- Rollback: drop the trigger and its function. The org_unit table
-- stays in place. squadron stops receiving updates from the
-- application; a manual one-shot `INSERT INTO squadron SELECT ... FROM
-- org_unit WHERE kind = 'SQUADRON' AND id NOT IN (SELECT id FROM
-- squadron)` plus the matching UPDATE/DELETE pair re-syncs the two
-- tables if needed before re-applying.

CREATE OR REPLACE FUNCTION sync_org_unit_to_squadron()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        IF NEW.kind = 'SQUADRON' THEN
            INSERT INTO squadron
                (id, version, created_at, updated_at, name, shorthand, description, active, is_promotion_enabled)
            VALUES
                (NEW.id, NEW.version, NEW.created_at, NEW.updated_at, NEW.name, NEW.shorthand, NEW.description, NEW.active, NEW.is_promotion_enabled)
            ON CONFLICT (id) DO UPDATE SET
                version              = EXCLUDED.version,
                created_at           = EXCLUDED.created_at,
                updated_at           = EXCLUDED.updated_at,
                name                 = EXCLUDED.name,
                shorthand            = EXCLUDED.shorthand,
                description          = EXCLUDED.description,
                active               = EXCLUDED.active,
                is_promotion_enabled = EXCLUDED.is_promotion_enabled;
        END IF;
        RETURN NEW;

    ELSIF (TG_OP = 'UPDATE') THEN
        -- If the discriminator flipped away from SQUADRON (would be a
        -- bug - org_unit.kind is application-immutable today), drop the
        -- legacy mirror so the two tables stay consistent. The reverse
        -- transition (NON-SQUADRON -> SQUADRON) is equally bug-prone but
        -- handled cleanly by the INSERT/UPSERT below.
        IF OLD.kind = 'SQUADRON' AND NEW.kind <> 'SQUADRON' THEN
            DELETE FROM squadron WHERE id = OLD.id;
        ELSIF NEW.kind = 'SQUADRON' THEN
            INSERT INTO squadron
                (id, version, created_at, updated_at, name, shorthand, description, active, is_promotion_enabled)
            VALUES
                (NEW.id, NEW.version, NEW.created_at, NEW.updated_at, NEW.name, NEW.shorthand, NEW.description, NEW.active, NEW.is_promotion_enabled)
            ON CONFLICT (id) DO UPDATE SET
                version              = EXCLUDED.version,
                created_at           = EXCLUDED.created_at,
                updated_at           = EXCLUDED.updated_at,
                name                 = EXCLUDED.name,
                shorthand            = EXCLUDED.shorthand,
                description          = EXCLUDED.description,
                active               = EXCLUDED.active,
                is_promotion_enabled = EXCLUDED.is_promotion_enabled;
        END IF;
        RETURN NEW;

    ELSIF (TG_OP = 'DELETE') THEN
        IF OLD.kind = 'SQUADRON' THEN
            -- The FK constraints from app_user / mission_participant /
            -- aggregate columns to squadron(id) are NO ACTION (the V1
            -- and V81-V83 declarations defaulted to that), so this
            -- DELETE will FAIL at the squadron-side level if any row
            -- still references the deleted Staffel. That failure
            -- propagates back to the org_unit DELETE statement and the
            -- whole transaction aborts - exactly the behaviour we want:
            -- the application should never hard-delete a referenced
            -- Squadron, only soft-delete via active = false.
            DELETE FROM squadron WHERE id = OLD.id;
        END IF;
        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_org_unit_to_squadron ON org_unit;
CREATE TRIGGER trg_sync_org_unit_to_squadron
AFTER INSERT OR UPDATE OR DELETE ON org_unit
FOR EACH ROW EXECUTE FUNCTION sync_org_unit_to_squadron();
