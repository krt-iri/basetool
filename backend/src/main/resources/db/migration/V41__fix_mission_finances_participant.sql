-- Drop the user_id foreign key and column
ALTER TABLE mission_finance_entry DROP CONSTRAINT fk_mfe_user;
ALTER TABLE mission_finance_entry DROP COLUMN user_id;

-- Add mission_participant_id column
ALTER TABLE mission_finance_entry ADD COLUMN mission_participant_id UUID NOT NULL;

-- Add Foreign Key to mission_participant
ALTER TABLE mission_finance_entry ADD CONSTRAINT fk_mfe_participant FOREIGN KEY (mission_participant_id) REFERENCES mission_participant(id) ON DELETE CASCADE;

-- Create index for performance
CREATE INDEX idx_mission_finance_entry_participant_id ON mission_finance_entry(mission_participant_id);
