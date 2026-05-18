-- =====================================================================
-- V83 - Job Order dual squadron model
-- =====================================================================
-- Why: a Job Order has two squadron references which can differ:
--   creating_squadron_id  - who authored the order in the system
--   requesting_squadron_id - on whose behalf the work is being done
-- This replaces the single legacy `squadron VARCHAR` column. The
-- replacement happens in three migrations across two releases per the
-- two-phase drop rule in db/migration/README.md:
--   V83 (this file)  - add the two FK columns + backfill, keep VARCHAR.
--   V85 (next release) - entity drops the VARCHAR field, migration
--                        relaxes NOT NULL on the column.
--   V86 (release after) - DROP COLUMN squadron.
--
-- Backfill:
--   creating_squadron_id  - trivially IRIDIUM (single-tenant past).
--   requesting_squadron_id - lookup the legacy VARCHAR against squadron
--                            name OR shorthand (case-insensitive), fall
--                            back to IRIDIUM for anything unresolved.

ALTER TABLE job_order ADD COLUMN creating_squadron_id   UUID;
ALTER TABLE job_order ADD COLUMN requesting_squadron_id UUID;

UPDATE job_order
   SET creating_squadron_id = '00000000-0000-0000-0000-000000000001'
 WHERE creating_squadron_id IS NULL;

UPDATE job_order jo
   SET requesting_squadron_id = COALESCE(
        (SELECT s.id
           FROM squadron s
          WHERE LOWER(s.name)      = LOWER(jo.squadron)
             OR LOWER(s.shorthand) = LOWER(jo.squadron)
          LIMIT 1),
        '00000000-0000-0000-0000-000000000001'
   )
 WHERE requesting_squadron_id IS NULL;

ALTER TABLE job_order
    ADD CONSTRAINT fk_job_order_creating_squadron
    FOREIGN KEY (creating_squadron_id) REFERENCES squadron(id);

ALTER TABLE job_order
    ADD CONSTRAINT fk_job_order_requesting_squadron
    FOREIGN KEY (requesting_squadron_id) REFERENCES squadron(id);
