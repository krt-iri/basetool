-- A mission participant's org-unit affiliation used to be a single FK
-- (mission_participant.squadron_id -> squadron) stamped at sign-up time. That
-- model could not express "this member belongs to a Staffel AND a
-- Spezialkommando", and it forced a wrong IRIDIUM fallback for admins with no
-- membership at all. This join table lets a participant reference zero, one, or
-- several org units (Staffel + SKs), pointing at the shared org_unit table so
-- both kinds are representable.
--
-- The legacy squadron_id column is intentionally NOT dropped here: per the
-- migration README's two-phase rule, the entity stops writing it in this
-- release (the JPA mapping is removed in the same commit) and the actual DROP
-- COLUMN lands in the later destructive-cleanup release that retargets the
-- remaining squadron FKs to org_unit.

CREATE TABLE mission_participant_org_unit (
    mission_participant_id UUID NOT NULL
        REFERENCES mission_participant (id) ON DELETE CASCADE,
    org_unit_id UUID NOT NULL
        REFERENCES org_unit (id),
    PRIMARY KEY (mission_participant_id, org_unit_id)
);

-- Reverse-lookup index for "which participants are affiliated with this org unit"
-- and to keep the FK on org_unit_id from forcing sequential scans on delete.
CREATE INDEX idx_mission_participant_org_unit_org_unit
    ON mission_participant_org_unit (org_unit_id);

-- Backfill the existing single-squadron stamp into the new join table. squadron_id
-- values have existed as kind='SQUADRON' rows in org_unit since V97, so the FK to
-- org_unit(id) resolves. Idempotent via ON CONFLICT so a re-run on a
-- partially-applied developer database is a no-op.
INSERT INTO mission_participant_org_unit (mission_participant_id, org_unit_id)
SELECT mp.id, mp.squadron_id
FROM mission_participant mp
WHERE mp.squadron_id IS NOT NULL
ON CONFLICT DO NOTHING;
