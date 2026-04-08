ALTER TABLE job_type ADD COLUMN is_leadership_role BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mission_participant DROP COLUMN mission_lead_id;
DROP TABLE mission_lead_type;
