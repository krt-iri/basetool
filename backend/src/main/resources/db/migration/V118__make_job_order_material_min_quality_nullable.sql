-- A job-order material may now request "Keine" (no minimum quality). NULL min_quality means
-- "no quality floor" -- inventory of any quality satisfies the requirement. Relaxing NOT NULL is
-- non-destructive (existing rows keep their value); the reader query treats NULL as no floor.
ALTER TABLE job_order_material ALTER COLUMN min_quality DROP NOT NULL;
