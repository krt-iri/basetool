-- Mission "Ablauf" (procedure timeline) + the short objective (Ziel) and rally-point
-- (Treffpunkt) free-text fields introduced with the final Einsatz design (#818 follow-up).
--
-- mission_step is an ordered, reorderable child of mission. order_index pins the position
-- explicitly (independent of insertion order); done is a shared completion flag every viewer
-- sees. The whole collection is guarded by the new mission.steps_version section counter
-- (a plain BIGINT, like core_version / schedule_version / flags_version / party_lead_version) so
-- editing the Ablauf never 409s a concurrent core/schedule/flags edit of the same mission.
CREATE TABLE mission_step (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    meta VARCHAR(200),
    done BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_mission_step_mission_order ON mission_step (mission_id, order_index);

-- Section-scoped optimistic-lock counter for the Ablauf editor / done-toggle.
ALTER TABLE mission ADD COLUMN steps_version BIGINT NOT NULL DEFAULT 0;

-- Short objective (Ziel) shown in the overview's "Mission auf einen Blick", distinct from the
-- long Markdown description; and the free-text rally point (Treffpunkt).
ALTER TABLE mission ADD COLUMN objective VARCHAR(250);
ALTER TABLE mission ADD COLUMN meeting_point VARCHAR(200);
