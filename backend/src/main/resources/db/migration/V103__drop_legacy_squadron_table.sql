-- SPEZIALKOMMANDO_PLAN.md §10 PR-7 / R8_DESTRUCTIVE_ROADMAP.md Step 6 — the
-- final destructive step. Drops the legacy squadron mirror table after
-- retargeting the three remaining FKs that still reference it to point at
-- org_unit instead. The V98 sync trigger + function that mirrored org_unit ->
-- squadron rows come out with the table.
--
-- *** REQUIRES R9 STEPS 1-5 IN PROD + FULL DB BACKUP ***
-- Irreversible. After this migration the legacy squadron table is gone and
-- every reference to a Squadron resolves via org_unit (kind='SQUADRON').
--
-- The PromotionTopic + MissionParticipant + JobOrderHandover Java entities
-- stay Squadron-typed -- Squadron extends OrgUnit via
-- @DiscriminatorValue('SQUADRON'), and the @JoinColumn keeps the legacy
-- column names (owning_squadron_id / squadron_id / executing_squadron_id) so
-- the constraint names + the V99 kind-guard trigger stay readable per
-- Plan §3.3.

-- 1. Retarget promotion_topic.owning_squadron_id FK from squadron(id) -> org_unit(id).
ALTER TABLE promotion_topic DROP CONSTRAINT IF EXISTS fk_promotion_topic_owning_squadron;
ALTER TABLE promotion_topic
    ADD CONSTRAINT fk_promotion_topic_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES org_unit(id);

-- 2. Retarget mission_participant.squadron_id FK from squadron(id) -> org_unit(id).
ALTER TABLE mission_participant DROP CONSTRAINT IF EXISTS mission_participant_squadron_id_fkey;
ALTER TABLE mission_participant
    ADD CONSTRAINT mission_participant_squadron_id_fkey
    FOREIGN KEY (squadron_id) REFERENCES org_unit(id);

-- 3. Retarget job_order_handover.executing_squadron_id FK from squadron(id) -> org_unit(id).
-- V84 added this audit-snapshot column with FK fk_job_order_handover_executing_squadron;
-- it stamps the executing user's squadron at handover time for cross-staffel audit trails.
ALTER TABLE job_order_handover DROP CONSTRAINT IF EXISTS fk_job_order_handover_executing_squadron;
ALTER TABLE job_order_handover
    ADD CONSTRAINT fk_job_order_handover_executing_squadron
    FOREIGN KEY (executing_squadron_id) REFERENCES org_unit(id);

-- 4. Drop the V98 sync trigger + function. With no foreign keys left pointing
-- at squadron(id), the mirror table has no consumers -- its only purpose was
-- to keep the dropped FKs resolving while the V97 / V100 / V101 / V102 / V103
-- chain rolled out.
DROP TRIGGER IF EXISTS trg_sync_org_unit_to_squadron ON org_unit;
DROP FUNCTION IF EXISTS sync_org_unit_to_squadron();

-- 5. Drop the squadron table. CASCADE is intentionally NOT used: by this
-- point every dependent constraint has been retargeted (steps 1-3) or
-- dropped in earlier migrations (V101 dropped the aggregate FKs, V102
-- dropped the app_user FK), so any remaining dependency is a bug worth
-- surfacing.
DROP TABLE squadron;
