-- Add new columns
ALTER TABLE mission_finance_entry ADD COLUMN mission_id UUID;
ALTER TABLE mission_finance_entry ADD COLUMN user_id UUID;

-- Update data based on existing mission_participant mapping
UPDATE mission_finance_entry mfe
SET mission_id = mp.mission_id,
    user_id = mp.user_id
FROM mission_participant mp
WHERE mfe.mission_participant_id = mp.id;

-- Clean up invalid rows
DELETE FROM mission_finance_entry WHERE user_id IS NULL OR mission_id IS NULL;

-- Set NOT NULL
ALTER TABLE mission_finance_entry ALTER COLUMN mission_id SET NOT NULL;
ALTER TABLE mission_finance_entry ALTER COLUMN user_id SET NOT NULL;

-- Add Foreign Keys
ALTER TABLE mission_finance_entry ADD CONSTRAINT fk_mfe_mission FOREIGN KEY (mission_id) REFERENCES mission(id) ON DELETE CASCADE;
ALTER TABLE mission_finance_entry ADD CONSTRAINT fk_mfe_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT;

-- Drop old column
ALTER TABLE mission_finance_entry DROP COLUMN mission_participant_id;

-- Rename description to note
ALTER TABLE mission_finance_entry RENAME COLUMN description TO note;

-- Type changes
ALTER TABLE mission_finance_entry ALTER COLUMN amount TYPE NUMERIC(19, 4);
ALTER TABLE mission_finance_entry ADD CONSTRAINT chk_mfe_amount_positive CHECK (amount >= 0);

-- Indexes
CREATE INDEX idx_mission_finance_entry_mission_id ON mission_finance_entry(mission_id);
CREATE INDEX idx_mission_finance_entry_user_id ON mission_finance_entry(user_id);
