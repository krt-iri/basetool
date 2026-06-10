-- V147 - Per-assignee notes on job orders (REQ-ORDERS-013)
--
-- Promotes the pure many-to-many join table `job_order_assignees` to a first-class
-- entity table so each (job order, assignee) edge can carry an optional free-text note
-- (e.g. "I work on this Friday" / "taking the refining part"). The note is editable by
-- the assignee themselves and by any Logistician-or-above who can see the order.
--
-- Schema change: add a surrogate UUID primary key, the optimistic-lock `version` column
-- and the audit timestamps that every AbstractEntity-mapped row needs (Hibernate
-- `ddl-auto=validate` would otherwise fail with "missing column [created_at]"), plus the
-- nullable `note` column. The former composite primary key (job_order_id, user_id) is
-- replaced by a UNIQUE constraint so a user is still an assignee at most once per order.

ALTER TABLE job_order_assignees
    ADD COLUMN id UUID;

UPDATE job_order_assignees
SET id = gen_random_uuid()
WHERE id IS NULL;

ALTER TABLE job_order_assignees
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE job_order_assignees
    DROP CONSTRAINT job_order_assignees_pkey;

ALTER TABLE job_order_assignees
    ADD CONSTRAINT job_order_assignees_pkey PRIMARY KEY (id);

ALTER TABLE job_order_assignees
    ADD CONSTRAINT uq_job_order_assignee UNIQUE (job_order_id, user_id);

ALTER TABLE job_order_assignees
    ADD COLUMN note VARCHAR(500);

ALTER TABLE job_order_assignees
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE job_order_assignees
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

ALTER TABLE job_order_assignees
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
