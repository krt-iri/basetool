-- Mission goals (Ziele) — a classified, ordered, reorderable list shown grouped on the mission
-- overview (Hauptziel -> Nebenziel -> Nicht-Ziel) and authored in the Verwaltung tab. Replaces the
-- single free-text mission.objective (Kurz-Ziel) introduced with V192: the structured goals take
-- over its role, and every existing non-empty objective is migrated into one PRIMARY goal so no
-- planning data is lost.
--
-- mission_objective mirrors mission_step: an ordered child of mission with an explicit order_index
-- (independent of insertion order). The whole collection is guarded by the new
-- mission.objectives_version section counter (a plain BIGINT, like core_version / schedule_version /
-- flags_version / steps_version) so editing the goals never 409s a concurrent core/schedule/flags/
-- Ablauf edit of the same mission. Unlike a step a goal has no "done" flag — it is a scope
-- statement, not a progress item; instead it carries a kind classification.
CREATE TABLE mission_objective (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    title VARCHAR(250) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    order_index INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_mission_objective_mission_order ON mission_objective (mission_id, order_index);

-- Section-scoped optimistic-lock counter for the goals editor.
ALTER TABLE mission ADD COLUMN objectives_version BIGINT NOT NULL DEFAULT 0;

-- Migrate every existing non-empty short objective (Ziel) into a single primary goal (Hauptziel).
-- order_index 0, kind PRIMARY; the goals editor takes over from here.
INSERT INTO mission_objective (
        id, mission_id, title, kind, order_index, version, created_at, updated_at, created_by)
SELECT gen_random_uuid(), id, objective, 'PRIMARY', 0, 0, now(), now(), 'system'
FROM mission
WHERE objective IS NOT NULL AND btrim(objective) <> '';

-- The single short objective is fully replaced by the structured goals above.
ALTER TABLE mission DROP COLUMN objective;
