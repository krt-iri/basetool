-- Creates a dedicated mission_ownership table that tracks the owner of a mission with its own
-- optimistic-lock version. This enables concurrent editing of the mission detail page without
-- owner changes invalidating other users' open forms (Option A / multi-user concurrency).
--
-- Mission.owner is additionally annotated with @OptimisticLock(excluded = true) on the entity
-- side, so that changing the owner does not bump Mission.version. Lost-update protection for
-- owner changes is now provided by mission_ownership.version.
CREATE TABLE mission_ownership (
    id          UUID PRIMARY KEY,
    version     BIGINT,
    created_at  TIMESTAMP WITH TIME ZONE,
    updated_at  TIMESTAMP WITH TIME ZONE,
    mission_id  UUID NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    owner_id    UUID REFERENCES app_user(id)
);

CREATE UNIQUE INDEX uk_mission_ownership_mission ON mission_ownership (mission_id);

-- Initial data sync: create one mission_ownership row per existing mission, mirroring the
-- current mission.owner_id. Uses gen_random_uuid() (pgcrypto) which is standard in PostgreSQL.
INSERT INTO mission_ownership (id, version, created_at, updated_at, mission_id, owner_id)
SELECT gen_random_uuid(), 0, NOW(), NOW(), m.id, m.owner_id
FROM mission m
WHERE NOT EXISTS (
    SELECT 1 FROM mission_ownership mo WHERE mo.mission_id = m.id
);

COMMENT ON TABLE mission_ownership IS
    'Companion 1:1 table to mission for tracking owner changes with an independent optimistic-lock version.';
COMMENT ON COLUMN mission_ownership.version IS
    'JPA @Version counter for optimistic locking on owner changes (independent of mission.version).';
