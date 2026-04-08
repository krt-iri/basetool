ALTER TABLE mission ADD COLUMN owner_id UUID REFERENCES app_user(id);

CREATE TABLE mission_managers (
    mission_id UUID NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    PRIMARY KEY (mission_id, user_id)
);

CREATE INDEX idx_mission_owner ON mission(owner_id);
CREATE INDEX idx_mission_managers_mission ON mission_managers(mission_id);
CREATE INDEX idx_mission_managers_user ON mission_managers(user_id);
