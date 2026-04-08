ALTER TABLE mission_lead_type ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE frequency_type (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE mission_frequency (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    frequency_type_id UUID NOT NULL REFERENCES frequency_type(id) ON DELETE CASCADE,
    frequency_value NUMERIC(5, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    UNIQUE(mission_id, frequency_type_id)
);
