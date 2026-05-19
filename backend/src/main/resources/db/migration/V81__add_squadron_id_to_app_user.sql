-- =====================================================================
-- V81 - User-to-Squadron membership
-- =====================================================================
-- Why: every member now belongs to a squadron, which gates which data
-- they see in the staffel-scoped aggregates (hangar, inventory, refinery,
-- missions, operations). Admins are not constrained by squadron - the
-- ROLE_ADMIN check at the service layer overrides squadron filters - so
-- the column stays nullable for them (and for guests who never carried
-- a squadron in the first place). Bestands-Backfill is IRIDIUM for
-- non-admin members; ADMIN role holders stay NULL so they default into
-- "all squadrons" mode (MULTI_SQUADRON_PLAN.md section 6).
--
-- The two UPDATEs below are idempotent: the first one resets admin rows
-- that an earlier development run of this migration may have backfilled
-- to IRIDIUM (the prior shape did not exclude admins), the second one
-- fills the remaining NULLs for non-admin members.

ALTER TABLE app_user ADD COLUMN squadron_id UUID;

-- Reset accidentally-backfilled admin rows to NULL. Safe to re-run on a
-- fresh DB (no rows match) and a no-op on a DB that never carried the
-- old shape.
UPDATE app_user u
   SET squadron_id = NULL
 WHERE squadron_id = '00000000-0000-0000-0000-000000000001'
   AND EXISTS (
       SELECT 1 FROM user_roles ur
         JOIN role r ON r.id = ur.role_id
        WHERE ur.user_id = u.id
          AND UPPER(r.name) = 'ADMIN'
   );

-- Backfill remaining non-admin members to IRIDIUM. Admins stay NULL so
-- SquadronScopeService.currentSquadronId() returns Optional.empty() for
-- them by default (= "all squadrons" view) and the switcher session
-- attribute selectively narrows the view when needed.
UPDATE app_user u
   SET squadron_id = '00000000-0000-0000-0000-000000000001'
 WHERE squadron_id IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM user_roles ur
         JOIN role r ON r.id = ur.role_id
        WHERE ur.user_id = u.id
          AND UPPER(r.name) = 'ADMIN'
   );

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_squadron
    FOREIGN KEY (squadron_id) REFERENCES squadron(id);
