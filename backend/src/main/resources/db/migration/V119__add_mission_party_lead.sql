-- Adds an optional "party lead" (Partyleiter) to a mission. Mirrors the participant model: the
-- party lead is either a linked registered user (party_lead_user_id) OR a free-text/anonymous
-- handle (party_lead_guest_name) when the person is not (yet) a registered member. At most one of
-- the two is set at a time; both NULL means no party lead is assigned.
--
-- party_lead_version is a section-scoped optimistic-lock counter in the same family as
-- core_version / schedule_version / flags_version (V77). It is marked @OptimisticLock(excluded =
-- true) on the entity so setting the party lead never bumps the global Mission.version and thus
-- does not invalidate other users' open forms on the same mission.

ALTER TABLE mission
    ADD COLUMN party_lead_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN party_lead_guest_name VARCHAR(100),
    ADD COLUMN party_lead_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_mission_party_lead_user ON mission(party_lead_user_id);

COMMENT ON COLUMN mission.party_lead_user_id IS
    'Optional FK to the registered user designated as party lead. Mutually exclusive with party_lead_guest_name. ON DELETE SET NULL so deleting a user does not cascade-delete missions.';
COMMENT ON COLUMN mission.party_lead_guest_name IS
    'Optional free-text/anonymous party-lead handle, used when the party lead is not a registered member. Mutually exclusive with party_lead_user_id.';
COMMENT ON COLUMN mission.party_lead_version IS
    'Section-scoped optimistic-lock counter for the party-lead endpoint (PUT /api/v1/missions/{id}/party-lead). Bumped by MissionService.setPartyLead; @OptimisticLock(excluded = true) so it never bumps the global Mission.version.';
