-- =====================================================================
-- V155 - Notification system (epic #622, Phase 1): notification
-- =====================================================================
-- Why: a generic, per-user notification inbox (REQ-NOTIF-001). Every row is
-- a single notification addressed to exactly one recipient, identified by the
-- Keycloak `sub` (the same UUID as app_user.id). The inbox is per-user
-- isolated (REQ-NOTIF-004) - it is NOT org-unit scoped, so there is no
-- owning_org_unit column and no FK to app_user: a recipient_sub is a loose
-- reference (bank-audit V154 precedent) so retention/cleanup, not delete
-- ordering, governs row lifetime.
--
-- `type` is the machine identifier the frontend renders via i18n keys; it is
-- deliberately NOT CHECK-constrained because the set grows with every new
-- producer and the JPA @Enumerated(STRING) mapping is the source of truth
-- (V113/V154 precedent). `params` holds a small JSON object of i18n render
-- parameters as plain TEXT (never queried into, P4kImportJob.result_json
-- precedent) - JSONB would buy nothing here. `entity_type` + `entity_id` are
-- a loose, FK-free back-reference to the originating aggregate so a deep-link
-- survives that aggregate's deletion.
--
-- Standard optimistic-locking columns (version/created_at/updated_at) back the
-- AbstractEntity mapping. read/read_at carry the per-user read state.
--
-- Rollback: DROP TABLE notification. Leaf table.

CREATE TABLE notification (
    id            UUID PRIMARY KEY,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    recipient_sub UUID                     NOT NULL,
    type          VARCHAR(64)              NOT NULL,
    params        TEXT,
    entity_type   VARCHAR(64),
    entity_id     UUID,
    is_read       BOOLEAN                  NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMP WITH TIME ZONE
);

-- The inbox lists a recipient's notifications most-recent-first.
CREATE INDEX idx_notification_recipient_created
    ON notification (recipient_sub, created_at DESC);

-- The always-on unread badge counts unread rows per recipient; a partial index
-- keeps that count cheap (REQ-NOTIF-006).
CREATE INDEX idx_notification_recipient_unread
    ON notification (recipient_sub)
    WHERE is_read = FALSE;

-- The retention sweep deletes read notifications past their cutoff (REQ-NOTIF-009).
CREATE INDEX idx_notification_read_at
    ON notification (read_at)
    WHERE is_read = TRUE;

COMMENT ON TABLE notification IS
    'Generic per-user notification inbox (epic #622, REQ-NOTIF-001). One row per recipient per event; isolated by recipient_sub, not org-unit scoped.';
COMMENT ON COLUMN notification.recipient_sub IS
    'Keycloak sub (= app_user.id) of the sole recipient; loose reference, no FK (REQ-NOTIF-004).';
COMMENT ON COLUMN notification.type IS
    'Machine notification type rendered by the frontend via i18n keys; @Enumerated(STRING) is the source of truth, no CHECK (growing set).';
COMMENT ON COLUMN notification.params IS
    'JSON object of i18n render parameters (plain TEXT, never queried into).';
COMMENT ON COLUMN notification.entity_type IS
    'Loose type tag of the originating aggregate (e.g. JOB_ORDER) for deep-linking; no FK.';
