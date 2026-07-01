-- Mission-specific ("custom") radio frequencies (REQ-MISSION-014). Until now every mission_frequency
-- row referenced a global frequency_type; a mission could only assign a value to one of the shared
-- "Frequenztypen". This migration makes the row dual-mode: it either references a global type (typed)
-- or carries its own free-text label (custom). Exactly one of the two is set, enforced by a check
-- constraint so the invariant cannot drift at the data layer.

-- 1) Custom label column (nullable — only custom rows use it, ≤100 chars mirroring the entity/DTO).
ALTER TABLE mission_frequency ADD COLUMN name VARCHAR(100);

-- 2) The type reference becomes optional so custom rows can leave it null. The existing
--    (mission_id, frequency_type_id) unique constraint still keeps at most one typed row per type;
--    PostgreSQL treats each NULL frequency_type_id as distinct, so multiple custom rows are allowed.
ALTER TABLE mission_frequency ALTER COLUMN frequency_type_id DROP NOT NULL;

-- 3) Exactly-one-of invariant: typed rows have a frequency_type_id and no name; custom rows have a
--    non-blank name and no frequency_type_id.
ALTER TABLE mission_frequency
    ADD CONSTRAINT ck_mission_frequency_typed_xor_named CHECK (
        (frequency_type_id IS NOT NULL AND name IS NULL)
        OR (frequency_type_id IS NULL AND name IS NOT NULL)
    );
