-- =====================================================================
-- V179 - Activity audit log (REQ-AUDIT-001, ADR-0037): audit_event
-- =====================================================================
-- Why: every audited mutation in the inventory / job-order / refinery /
-- personal-inventory / mission / operation areas writes exactly one row to this insert-only
-- table, mirroring bank_audit_event (V154): no version column, no
-- updates, occurred_at is the single timestamp. The actor is stored
-- twice - as an FK (SET NULL on user deletion) AND as a denormalized
-- handle snapshot - because the trail must survive user deletion. The
-- event type and domain are deliberately NOT CHECK-constrained: both
-- enums grow with the domains and the JPA @Enumerated(STRING) mapping is
-- the source of truth (V113/V154 precedent).
--
-- One physical table, six logical logs: the `domain` discriminator
-- keeps the logs separate for the admin viewer's tabs and the per-area
-- PDF export (ADR-0037). The bank keeps its own bank_audit_event table.
--
-- The audit log is business data readable ONLY by admins (REQ-AUDIT-001);
-- reference columns (subject_id, target_user_id) are plain UUIDs (no FKs)
-- so audit rows outlive any referenced aggregate (job orders are
-- hard-deleted, inventory rows are depleted) without delete-ordering
-- constraints.
--
-- Rollback: DROP TABLE audit_event. Leaf table.

CREATE TABLE audit_event (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    domain         VARCHAR(30)              NOT NULL,
    event_type     VARCHAR(60)              NOT NULL,
    actor_user_id  UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    actor_handle   VARCHAR(255)             NOT NULL,
    subject_id     UUID,
    subject_label  VARCHAR(255),
    target_user_id UUID,
    details        TEXT
);

-- The admin viewer lists one domain at a time, most-recent-first, and filters by period.
CREATE INDEX idx_audit_event_domain_occurred
    ON audit_event (domain, occurred_at DESC);

-- Per-actor filtering in the admin viewer.
CREATE INDEX idx_audit_event_actor
    ON audit_event (actor_user_id);

COMMENT ON TABLE audit_event IS
    'Immutable, admin-only activity audit trail across six areas (REQ-AUDIT-001, ADR-0037). One row per audited mutation; insert-only, no version.';
COMMENT ON COLUMN audit_event.domain IS
    'Functional area discriminator: INVENTORY, JOB_ORDER, REFINERY, PERSONAL_INVENTORY, MISSION, OPERATION.';
COMMENT ON COLUMN audit_event.actor_handle IS
    'Denormalized actor handle snapshot - the trail survives user deletion.';
COMMENT ON COLUMN audit_event.subject_label IS
    'Denormalized human-readable label of the affected aggregate - survives aggregate deletion.';
COMMENT ON COLUMN audit_event.details IS
    'Compact human-readable details payload (amounts, counts, parameters); never user free text.';
