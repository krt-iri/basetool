CREATE TABLE job_order_assignees (
    job_order_id UUID NOT NULL REFERENCES job_order (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (job_order_id, user_id)
);