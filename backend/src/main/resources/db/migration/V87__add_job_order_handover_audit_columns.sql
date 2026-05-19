-- =====================================================================
-- V87 - JobOrderHandover audit columns (executing user + squadron)
-- =====================================================================
-- Numbered V87 to leave V84-V86 reserved for the Phase 7 tightening /
-- legacy-VARCHAR drop chain in MULTI_SQUADRON_PLAN.md section 10.
-- Why: MULTI_SQUADRON_PLAN.md section 4.4 mandates that a handover
-- protocols the executing user inclusive of their squadron as an audit
-- information. The cross-staffel workspace lets a Logistician from
-- squadron A perform a handover on an order linked to InventoryItems
-- of squadron B; without the executing-user stamp the audit trail does
-- not record who actually carried out the write.
--
-- Phase 1 of the two-phase rollout: columns added as nullable + no
-- backfill (the audit trail starts now; historical rows stay NULL
-- because we have no truthful source to derive them from). A future
-- migration may tighten {executing_user_id} to NOT NULL once every
-- historical handover has been audit-stamped retroactively (the
-- application code never re-writes handover rows, so retro-stamping
-- would have to be a manual operator action).
--
-- The FK on app_user uses ON DELETE SET NULL because deleting the user
-- account must not break the handover audit trail: we lose "who did
-- it" but keep "the handover happened, here is the recipient + items".
-- The squadron FK has no ON DELETE clause; squadron rows are soft-
-- archived via squadron.active=false rather than deleted (the audit
-- snapshot stays meaningful even when the squadron is no longer active).

ALTER TABLE job_order_handover
    ADD COLUMN executing_user_id     UUID,
    ADD COLUMN executing_squadron_id UUID;

ALTER TABLE job_order_handover
    ADD CONSTRAINT fk_job_order_handover_executing_user
    FOREIGN KEY (executing_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE job_order_handover
    ADD CONSTRAINT fk_job_order_handover_executing_squadron
    FOREIGN KEY (executing_squadron_id) REFERENCES squadron(id);
