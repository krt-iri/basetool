-- =====================================================================
-- V171 - Org-chart free-text holder names (epic #692 follow-on, REQ-ORG-020).
-- =====================================================================
-- Why: a functional-rank position can now name a Kartell member who has no
-- Basetool account yet, via a free-text display_name, instead of binding a
-- user_id. Once that member gets an account, reassigning the position to the
-- account clears display_name in the same UPDATE -- no new row, no structural
-- change, so the swap is regression-free.
--
-- Model: a person-position is filled by EXACTLY ONE of an account (user_id) or
-- a free-text name (display_name), never both. The pre-existing leaderless-
-- Kommando escape (a COMMAND_LEAD with neither) stays valid. display_name is
-- deliberately distinct from the existing name column, which remains the
-- Kommandogruppen-Name (COMMAND_LEAD only, chk_org_chart_name unchanged).
--
-- Rights: org_chart_position stays purely descriptive -- the authority cascade
-- runs entirely off org_unit_membership (user_id NOT NULL), never off the chart
-- -- so a free-text row (user_id NULL) grants nothing, exactly like a leaderless
-- Kommando does today.
--
-- Indexes: uq_org_chart_user_per_unit / uq_org_chart_user_in_area already filter
-- WHERE user_id IS NOT NULL, so free-text rows sit outside user-uniqueness; no
-- index change is needed and display_name gets no uniqueness (typed names may
-- repeat on a descriptive chart). The cardinality/singleton indexes key on
-- position_type, so a free-text holder still occupies its single slot.
--
-- Idempotency: DROP CONSTRAINT IF EXISTS before ADD; ADD COLUMN IF NOT EXISTS.
-- Rollback: drop display_name + chk_org_chart_holder and restore the V138
-- chk_org_chart_user body; no data created.

ALTER TABLE org_chart_position
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(120);

-- A position is never held by both an account and a typed name at once.
ALTER TABLE org_chart_position DROP CONSTRAINT IF EXISTS chk_org_chart_holder;
ALTER TABLE org_chart_position
    ADD CONSTRAINT chk_org_chart_holder CHECK (
        user_id IS NULL OR display_name IS NULL
    );

-- Tighten the "holder optional only on a Kommando" rule so a typed name counts
-- as filled: every non-COMMAND_LEAD person-position needs a user_id OR a
-- display_name. A COMMAND_LEAD may still be fully leaderless (neither set).
ALTER TABLE org_chart_position DROP CONSTRAINT IF EXISTS chk_org_chart_user;
ALTER TABLE org_chart_position
    ADD CONSTRAINT chk_org_chart_user CHECK (
        user_id IS NOT NULL
        OR display_name IS NOT NULL
        OR position_type = 'COMMAND_LEAD'
    );
