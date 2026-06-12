-- =====================================================================
-- V154 - Kartell bank (epic #556, Phase 1): bank_audit_event
-- =====================================================================
-- Why: every bank mutation writes exactly one row to this insert-only audit
-- table (REQ-BANK-012), modeled after external_sync_report (V113): no version
-- column, no updates, occurred_at is the single timestamp. The actor is
-- stored twice - as an FK (SET NULL on user deletion) AND as a denormalized
-- handle snapshot - because the trail must survive user deletion. The event
-- type is deliberately NOT CHECK-constrained: the enum grows with later
-- phases (PDF exports in Phase 3, wipe reset in Phase 4) and the JPA
-- @Enumerated(STRING) mapping is the source of truth (V113 precedent).
--
-- The audit log is business data readable ONLY by admins (REQ-BANK-012);
-- reference columns are plain UUIDs (no FKs) so audit rows outlive any
-- referenced aggregate without delete-ordering constraints.
--
-- Rollback: DROP TABLE bank_audit_event. Leaf table.

CREATE TABLE bank_audit_event (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id  UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    actor_handle   VARCHAR(255)             NOT NULL,
    event_type     VARCHAR(40)              NOT NULL,
    account_id     UUID,
    transaction_id UUID,
    target_user_id UUID,
    details        TEXT
);

-- The admin viewer lists most-recent-first and filters by period.
CREATE INDEX idx_bank_audit_event_occurred
    ON bank_audit_event (occurred_at DESC);

-- Per-account filtering in the admin viewer.
CREATE INDEX idx_bank_audit_event_account
    ON bank_audit_event (account_id, occurred_at DESC);

COMMENT ON TABLE bank_audit_event IS
    'Immutable, admin-only bank audit trail (epic #556, REQ-BANK-012). One row per bank mutation; insert-only, no version.';
COMMENT ON COLUMN bank_audit_event.actor_handle IS
    'Denormalized actor handle snapshot - the trail survives user deletion.';
COMMENT ON COLUMN bank_audit_event.details IS
    'Compact human-readable details payload (amounts, before/after flags, parameters).';
