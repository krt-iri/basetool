-- =====================================================================
-- V85 - Squadron scope on the promotion system
-- =====================================================================
-- Why: the promotion-topic hierarchy (topic → category → level-content)
-- plus the loose rank-requirement and member-evaluation rows hanging off
-- it become squadron-scoped so an Officer of squadron X can manage
-- their own squadron's promotion criteria without seeing or editing
-- squadron Y's. Only the top-level {@code promotion_topic} carries the
-- squadron FK; categories / level-content / rank-requirement /
-- member-evaluation inherit the scope through their topic reference
-- (Plan §3.2 "no denormalisation" rule).
--
-- This migration ships with Phase 6 (this release), so it MUST land
-- before the Phase-7 cleanup chain (`V86`/`V87`/`V88`, see
-- MULTI_SQUADRON_PLAN.md section 10) which is reserved for the
-- post-prod tightening + legacy `job_order.squadron` VARCHAR drop —
-- Flyway's strict ascending order (`out-of-order=false` is the project
-- default) would otherwise refuse to apply the later cleanup migrations
-- after a higher-numbered Phase-6 migration. Follows the same two-phase
-- pattern as V82: column added nullable + backfilled to IRIDIUM, NOT
-- NULL tightening deferred until the post-prod cleanup release.

ALTER TABLE promotion_topic ADD COLUMN owning_squadron_id UUID;

UPDATE promotion_topic
   SET owning_squadron_id = '00000000-0000-0000-0000-000000000001'
 WHERE owning_squadron_id IS NULL;

ALTER TABLE promotion_topic
    ADD CONSTRAINT fk_promotion_topic_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);
