-- =====================================================================
-- V81 - User-to-Squadron membership
-- =====================================================================
-- Why: every member now belongs to a squadron, which gates which data
-- they see in the staffel-scoped aggregates (hangar, inventory, refinery,
-- missions, operations). Admins are not constrained by squadron - the
-- ROLE_ADMIN check at the service layer overrides squadron filters - so
-- the column stays nullable for them (and for guests who never carried
-- a squadron in the first place). Bestands-Backfill is IRIDIUM because
-- the system was single-tenant until this release.

ALTER TABLE app_user ADD COLUMN squadron_id UUID;

UPDATE app_user
   SET squadron_id = '00000000-0000-0000-0000-000000000001'
 WHERE squadron_id IS NULL;

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_squadron
    FOREIGN KEY (squadron_id) REFERENCES squadron(id);
