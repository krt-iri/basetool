CREATE TABLE job_order_handover (
    id UUID PRIMARY KEY,
    job_order_id UUID NOT NULL,
    handover_time TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_handle VARCHAR(255) NOT NULL,
    recipient_squadron VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_job_order_handover_job_order FOREIGN KEY (job_order_id) REFERENCES job_order (id)
);

CREATE TABLE job_order_handover_item (
    id UUID PRIMARY KEY,
    job_order_handover_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_job_order_handover_item_handover FOREIGN KEY (job_order_handover_id) REFERENCES job_order_handover (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_order_handover_item_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (id)
);
