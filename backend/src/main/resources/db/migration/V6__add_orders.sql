CREATE TABLE job_order (
    id UUID PRIMARY KEY,
    squadron VARCHAR(255) NOT NULL,
    handle VARCHAR(255) NOT NULL,
    priority INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE job_order_material (
    id UUID PRIMARY KEY,
    job_order_id UUID NOT NULL,
    material_id UUID NOT NULL,
    min_quality INT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_job_order FOREIGN KEY (job_order_id) REFERENCES job_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_material FOREIGN KEY (material_id) REFERENCES material(id)
);
