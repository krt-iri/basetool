-- =====================================================================
-- V172 - app_user approval state + audit (epic #720, Track 1, REQ-SEC-017)
-- =====================================================================
-- Why: a brand-new Discord registration must land in a holding state with no
-- access until an admin approves it (REQ-SEC-017). approval_status carries that
-- lifecycle (PENDING -> ACTIVE | REJECTED); approved_at / approved_by_id record
-- the decision; user_approval_event is the append-only audit of every decision.
--
-- Backfill: every pre-existing user is an already-vetted member, so they are set
-- ACTIVE. Only NEW Discord registrations after this migration start PENDING (the
-- decision is made in code: credential/admin-created users stay ACTIVE, a
-- Discord-federated non-admin first login becomes PENDING). The column DEFAULT is
-- the fail-closed PENDING for any non-application insert; the application always
-- writes the column explicitly.

ALTER TABLE app_user
    ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN approved_at     TIMESTAMP WITH TIME ZONE,
    ADD COLUMN approved_by_id  UUID;

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_approval_status
        CHECK (approval_status IN ('PENDING', 'ACTIVE', 'REJECTED'));

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_approved_by
        FOREIGN KEY (approved_by_id) REFERENCES app_user (id);

-- Every existing row is an already-vetted member.
UPDATE app_user SET approval_status = 'ACTIVE' WHERE approval_status = 'PENDING';

COMMENT ON COLUMN app_user.approval_status IS
    'Account lifecycle: PENDING (Discord registration awaiting admin approval), ACTIVE, REJECTED. Epic #720 / REQ-SEC-017.';

CREATE TABLE user_approval_event (
    id            UUID PRIMARY KEY,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    user_id       UUID NOT NULL REFERENCES app_user (id),
    decision      VARCHAR(16) NOT NULL,
    reason        TEXT,
    decided_by_id UUID REFERENCES app_user (id),
    CONSTRAINT chk_user_approval_event_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX idx_user_approval_event_user_id ON user_approval_event (user_id);
