-- Phase 4 of the Job-Order rework (#344): the material-claim ("Eintragung") domain.
--
-- A claim records that a squadron signs up to deliver a partial quantity of one material bucket on a
-- public Spezialkommando order. Keyed on the aggregated bucket (job_order, material,
-- quality_requirement) so it works uniformly for MATERIAL and ITEM orders. Signal-only — claims
-- never move inventory; the open-remaining math and all invariants (SK-only, no overclaim,
-- terminal-status freeze) live in MaterialClaimService.
--
-- Concurrency: claims are an independent aggregate (no mapped collection on JobOrder), so mutating a
-- claim never bumps the parent order's @Version. The reconciliation hooks (de-escalation withdrawal,
-- orphaned-bucket withdrawal) delete through MaterialClaimRepository, not via a JPA cascade.
CREATE TABLE material_claim (
    id                   UUID                     PRIMARY KEY,
    version              BIGINT                   NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    job_order_id         UUID                     NOT NULL REFERENCES job_order(id),
    material_id          UUID                     NOT NULL REFERENCES material(id),
    quality_requirement  VARCHAR(8)               NOT NULL,
    claiming_org_unit_id UUID                     NOT NULL REFERENCES org_unit(id),
    amount               DOUBLE PRECISION         NOT NULL,
    claimed_by_user_id   UUID                     REFERENCES app_user(id),
    CONSTRAINT chk_material_claim_amount_positive CHECK (amount > 0)
);

-- One claim per (bucket, squadron): a squadron raising its stake updates the existing row instead of
-- inserting a duplicate (enforced again in the service via find-then-update; this is the DB backstop
-- against a TOCTOU double-insert race).
CREATE UNIQUE INDEX uq_material_claim_bucket_org_unit
    ON material_claim(job_order_id, material_id, quality_requirement, claiming_org_unit_id);

-- Bucket-scoped aggregate reads (open-remaining computation, claim list per order) that omit
-- claiming_org_unit_id; keeps the planner on an index rather than a job-order-wide scan.
CREATE INDEX idx_material_claim_bucket
    ON material_claim(job_order_id, material_id, quality_requirement);
