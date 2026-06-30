-- "Einsatzleiter" (mission lead) designation on the job_type reference table. Exactly one job type
-- may be designated as the mission lead; the mission overview's facts-bar "Leiter" then shows the
-- participant whose planned mission job type is that designated type (falling back to the mission
-- owner). The designation is admin-set on a MISSION leadership role.
ALTER TABLE job_type ADD COLUMN is_mission_lead BOOLEAN NOT NULL DEFAULT FALSE;

-- Enforce "only one Einsatzleiter type" at the database level: the partial index covers only the
-- rows where is_mission_lead is true, so two such rows would collide on the (constant-true) indexed
-- value. Re-designating moves the flag (the service clears the previous holder first).
CREATE UNIQUE INDEX ux_job_type_single_mission_lead ON job_type (is_mission_lead)
    WHERE is_mission_lead;
