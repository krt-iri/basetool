-- Adds a section-scoped optimistic-lock counter for the mission owning-org-unit reassignment
-- endpoint (PUT /api/v1/missions/{id}/owning-org-unit, REQ-ORG-018 / ADR-0050). The
-- owning_org_unit_id column itself already exists (it has been writable on the entity since R9
-- Step 2 dropped the legacy owning_squadron_id mirror); this migration only adds the dedicated
-- version counter that guards concurrent reassignments.
--
-- owning_org_unit_version is in the same family as core_version / schedule_version / flags_version
-- / party_lead_version / steps_version. It is marked @OptimisticLock(excluded = true) on the entity
-- so re-homing the mission never bumps the global Mission.version and thus does not invalidate other
-- users' open forms on the same mission.

ALTER TABLE mission
    ADD COLUMN owning_org_unit_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN mission.owning_org_unit_version IS
    'Section-scoped optimistic-lock counter for the owning-org-unit reassignment endpoint (PUT /api/v1/missions/{id}/owning-org-unit). Bumped by MissionService.updateOwningOrgUnit; @OptimisticLock(excluded = true) so it never bumps the global Mission.version.';
