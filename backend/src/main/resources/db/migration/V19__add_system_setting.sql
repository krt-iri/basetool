CREATE TABLE system_setting (
    setting_key VARCHAR(255) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_setting (setting_key, setting_value, version) VALUES ('job_order.age_yellow_days', '30', 0);
INSERT INTO system_setting (setting_key, setting_value, version) VALUES ('job_order.age_red_days', '90', 0);
