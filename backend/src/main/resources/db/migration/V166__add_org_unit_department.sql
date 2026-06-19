-- =====================================================================
-- V166 - Org hierarchy R3 (epic #692, REQ-ORG-018): the Bereich's frozen
--        department / Bereichsfarbe. PURELY ADDITIVE.
-- =====================================================================
-- Why: REQ-ORG-018 tints each Bereich's org-chart nodes with its frozen
-- Bereichsfarbe. The colour is identified by a fixed department enum
-- (Profit, Sub-Radar, Raumueberlegenheit, Forschung, Marinekorps,
-- Search & Rescue) that maps one-to-one to the design-system
-- --color-dept-* tokens. This migration adds the nullable column that
-- stores it; an admin assigns a department when creating / editing a
-- Bereich. NULL is the additive-soak default: a Bereich without a
-- department (and every non-Bereich kind) renders untinted, so the chart
-- behaves exactly as today until departments are assigned.
--
-- The CHECK pins the column to BEREICH rows only and to the closed enum
-- set, so a Squadron/SK/OL row can never carry a department and a typo'd
-- value is rejected at write time (defence in depth alongside the JPA
-- @Enumerated(STRING) mapping). A single-row CHECK suffices — no cross-row
-- rule is involved.
--
-- Idempotency: ADD COLUMN IF NOT EXISTS; DROP CONSTRAINT IF EXISTS before
-- ADD. Rollback: drop the constraint and the column; no data created.

ALTER TABLE org_unit ADD COLUMN IF NOT EXISTS department VARCHAR(32);

ALTER TABLE org_unit DROP CONSTRAINT IF EXISTS chk_org_unit_department;
ALTER TABLE org_unit ADD CONSTRAINT chk_org_unit_department
    CHECK (
        department IS NULL
        OR (
            kind = 'BEREICH'
            AND department IN (
                'PROFIT', 'SUB_RADAR', 'RAUMUEBERLEGENHEIT',
                'FORSCHUNG', 'MARINEKORPS', 'SEARCH_RESCUE'
            )
        )
    );
