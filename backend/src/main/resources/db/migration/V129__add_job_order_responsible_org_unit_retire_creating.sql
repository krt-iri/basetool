-- Phase 2 of the Job-Order rework (#342): introduce the responsible (processing) org unit and
-- retire the creating (author) org unit.
--
-- 1) responsible_org_unit_id: the org unit that processes the order (squadron OR Spezialkommando).
--    Only profit-eligible units are valid responsibles; that is enforced in the application layer
--    (org_unit.is_profit_eligible, added in V128). Added NULLABLE here: pre-existing orders are
--    backfilled and the column is tightened to NOT NULL in Phase 3 (#343). New orders always stamp
--    it at create time.
ALTER TABLE job_order
    ADD COLUMN responsible_org_unit_id UUID;
ALTER TABLE job_order
    ADD CONSTRAINT fk_job_order_responsible_org_unit
        FOREIGN KEY (responsible_org_unit_id) REFERENCES org_unit (id);
CREATE INDEX idx_job_order_responsible_org_unit_id ON job_order (responsible_org_unit_id);

-- 2) Retire creating_org_unit_id (the order author): the rework drops the creating-org-unit concept
--    entirely. The application stops reading and writing this column from this release on; the
--    column itself is dropped in the destructive cleanup release (two-phase destructive rule, see
--    db/migration/README.md). Relax the NOT NULL constraint so inserts that no longer supply the
--    column succeed during the soak.
ALTER TABLE job_order
    ALTER COLUMN creating_org_unit_id DROP NOT NULL;
