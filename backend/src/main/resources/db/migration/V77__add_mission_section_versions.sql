-- Introduces three independent, section-scoped optimistic-lock counters on the mission
-- aggregate. They replace the previously-shared Mission.@Version for the dedicated section
-- patch endpoints (/core, /schedule, /flags), so two users editing different sections of the
-- same mission no longer invalidate each other's open forms with a 409 conflict.
--
-- Mission.@Version itself stays in place as a technical safety net for the few remaining
-- non-section attributes (e.g. parent, sub-mission tree) but is no longer the source of truth
-- for the section patch endpoints. The Java-side mapping marks the new columns and the
-- operation FK as @OptimisticLock(excluded = true) so that updates to them only bump the
-- corresponding section counter and never the global Mission.version.
--
-- Rationale and detailed mechanics: see CHANGELOG.md (Stufe 1 / section-version rollout)
-- and the long-form documentation comment on MissionService.

ALTER TABLE mission
    ADD COLUMN core_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN schedule_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN flags_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN mission.core_version IS
    'Section-scoped optimistic-lock counter for the core (name/description/status/calendar/operation) patch endpoint. Bumped by MissionService.updateCoreSection and by status-driven auto-transitions on the core section.';
COMMENT ON COLUMN mission.schedule_version IS
    'Section-scoped optimistic-lock counter for the schedule (meeting/planned/actual times) patch endpoint. Bumped by MissionService.updateScheduleSection and by status-driven auto-transitions that set actualStartTime.';
COMMENT ON COLUMN mission.flags_version IS
    'Section-scoped optimistic-lock counter for the flags (isInternal) patch endpoint.';
