-- =====================================================================
-- V86 - Per-squadron feature flag: promotion-system enabled
-- =====================================================================
-- Why: each squadron may now decide whether the full promotion subtree
-- (topics, categories, level-contents, rank-requirements, member
-- evaluations) is visible and usable for its non-admin members.
-- Flipping the flag does NOT destroy any data — categories, ranks and
-- evaluations stay in the DB and become visible again once the flag is
-- flipped back on. Admins always retain full access regardless of the
-- flag so they can restore visibility for a squadron whose officers
-- accidentally locked themselves out.
--
-- Default is TRUE so the migration is a no-op behaviour change: every
-- existing squadron continues to expose the promotion menu after the
-- migration runs. Admins must explicitly switch a squadron OFF in
-- /admin/settings → "Beförderungssystem pro Staffel" if they want to
-- hide the feature.
--
-- Slot choice: takes V86 ahead of the reserved Phase-7 cleanup chain
-- (V87 NOT-NULL tightening + V88 stop-write job_order.squadron VARCHAR
-- + V89 DROP COLUMN), same renumbering pattern V85 followed when it
-- shipped — Flyway's strict ascending order (`out-of-order=false` is
-- the project default) would otherwise refuse the later cleanup
-- migrations after a higher-numbered Phase-6 migration.

ALTER TABLE squadron
    ADD COLUMN is_promotion_enabled BOOLEAN NOT NULL DEFAULT TRUE;
