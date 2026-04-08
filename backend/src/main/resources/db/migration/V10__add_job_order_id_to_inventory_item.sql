ALTER TABLE inventory_item ADD COLUMN job_order_id UUID;
ALTER TABLE inventory_item ADD CONSTRAINT fk_inventory_item_job_order FOREIGN KEY (job_order_id) REFERENCES job_order(id) ON DELETE SET NULL;
