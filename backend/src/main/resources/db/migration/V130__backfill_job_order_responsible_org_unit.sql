-- Phase 3 of the Job-Order rework (#343): backfill the responsible (processing) org unit on every
-- pre-existing order and tighten the column to NOT NULL.
--
-- Background: V129 added responsible_org_unit_id as NULLABLE and relaxed the legacy
-- creating_org_unit_id to nullable. Orders created from the Phase-2 release on stamp
-- responsible_org_unit_id directly; orders that predate Phase 2 still carry only the retired
-- creating_org_unit_id (the former author). The rework's decision #11 keeps history private: each
-- legacy order becomes responsible to whatever org unit created it, so it stays visible exactly to
-- that squadron + admins (squadron-private) and does not leak cross-staffel.
--
-- 1) Backfill: copy the retired creating org unit onto the new responsible column wherever the
--    latter is still NULL (i.e. every pre-Phase-2 row). Post-Phase-2 rows already have it set.
UPDATE job_order
SET responsible_org_unit_id = creating_org_unit_id
WHERE responsible_org_unit_id IS NULL
  AND creating_org_unit_id IS NOT NULL;

-- 2) Tighten to NOT NULL. After the backfill above every row has a responsible org unit: pre-Phase-2
--    rows from creating_org_unit_id, Phase-2+ rows from the create-time stamp. A guest order created
--    after V128 always routes onto the configured intake Spezialkommando, so no row can reach this
--    point without a responsible. The application has enforced this invariant on every insert since
--    Phase 2, so the constraint cannot be violated by in-flight writes during deploy.
ALTER TABLE job_order
    ALTER COLUMN responsible_org_unit_id SET NOT NULL;
