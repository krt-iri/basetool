-- =====================================================================
-- V193 - Bank: per-account approval limits, user-initiated transfer
--        requests and the two-step owner approval (REQ-BANK-039/-040/-041)
-- =====================================================================
-- Why: three coupled additions on top of the org-unit bank side, all still
-- mediated exclusively by the OrgUnitBankAccessService seam (ADR-0020) so the
-- bank itself stays org-unit-blind (REQ-BANK-008, ADR-0011):
--
--  1. bank_account_approval_limit - a per-account, per-visibility-tier ceiling
--     (whole aUEC, >= 0) up to which that tier may raise a booking request
--     WITHOUT the responsible holder's explicit approval (REQ-BANK-041). The
--     tier dimension mirrors bank_account_view_grant (V189) one-for-one
--     (grantee_kind + role_code / grantee_user_id): MEMBERSHIP_ROLE / GLOBAL_ROLE
--     / USER / ALL_MEMBERS. A missing limit = unlimited (no approval needed),
--     preserving today's behaviour. Set by the responsible holder, bank
--     management or admin; never by a plain bank employee.
--
--  2. bank_booking_request gains a TRANSFER type and a target_account_id so a
--     viewer of a (source) account may request a transfer to ANY active account
--     (REQ-BANK-040); the destination + both holders are recorded by the bank
--     employee on confirmation via the existing bookTransfer ledger path.
--
--  3. The two-step owner approval (REQ-BANK-041): requires_owner_approval +
--     applicable_limit are snapshotted on the request at creation (the seam
--     resolves the requester's tier limit; the blind confirm path only reads the
--     boolean). owner_approval_granted (+ who/when) records the responsible
--     holder granting approval in-app from the "Fremde Anträge" tab, which
--     pre-fills the bank employee's confirmation checkbox.
--
-- type's CHECK is widened to add TRANSFER (the set stays closed, mirroring
-- bank_account.type, V150). All new bank_booking_request columns are nullable /
-- defaulted so existing rows keep today's semantics with no backfill.
--
-- Rollback: DROP TABLE bank_account_approval_limit;
--           ALTER TABLE bank_booking_request DROP COLUMN target_account_id,
--             requires_owner_approval, applicable_limit, owner_approval_granted,
--             owner_approval_granted_by, owner_approval_granted_by_handle,
--             owner_approval_granted_at; restore the original type CHECK.

-- ---------------------------------------------------------------------
-- 1. Per-account, per-tier approval limit
-- ---------------------------------------------------------------------
CREATE TABLE bank_account_approval_limit (
    id              UUID PRIMARY KEY,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    account_id      UUID                     NOT NULL REFERENCES bank_account (id) ON DELETE CASCADE,
    grantee_kind    VARCHAR(16)              NOT NULL,
    role_code       VARCHAR(64),
    grantee_user_id UUID                     REFERENCES app_user (id) ON DELETE CASCADE,
    limit_amount    NUMERIC(19, 4)           NOT NULL,
    CONSTRAINT chk_bank_appr_limit_kind
        CHECK (grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE', 'USER', 'ALL_MEMBERS')),
    -- The populated columns must match the grantee kind (mirrors chk_bank_view_grant_payload).
    CONSTRAINT chk_bank_appr_limit_payload
        CHECK (
            (grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE')
                AND role_code IS NOT NULL AND grantee_user_id IS NULL)
            OR (grantee_kind = 'USER'
                AND grantee_user_id IS NOT NULL AND role_code IS NULL)
            OR (grantee_kind = 'ALL_MEMBERS'
                AND role_code IS NULL AND grantee_user_id IS NULL)
        ),
    -- Whole-aUEC ceiling; 0 = every request of that tier needs approval.
    CONSTRAINT chk_bank_appr_limit_nonneg
        CHECK (limit_amount >= 0)
);

-- One limit per (account, role-kind, role): setting a role bucket is an upsert.
CREATE UNIQUE INDEX uq_bank_appr_limit_role
    ON bank_account_approval_limit (account_id, grantee_kind, role_code)
    WHERE grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE');

-- One limit per (account, user): setting the same user twice is an upsert.
CREATE UNIQUE INDEX uq_bank_appr_limit_user
    ON bank_account_approval_limit (account_id, grantee_user_id)
    WHERE grantee_kind = 'USER';

-- At most one all-members limit per account.
CREATE UNIQUE INDEX uq_bank_appr_limit_all_members
    ON bank_account_approval_limit (account_id)
    WHERE grantee_kind = 'ALL_MEMBERS';

-- Limits are resolved per account ("what may this requester book without approval?").
CREATE INDEX idx_bank_appr_limit_account
    ON bank_account_approval_limit (account_id);

COMMENT ON TABLE bank_account_approval_limit IS
    'Per-account, per-visibility-tier approval ceiling (REQ-BANK-041): up to limit_amount a tier may request without the responsible holder''s approval. Tier dimension mirrors bank_account_view_grant (V189); missing row = unlimited.';
COMMENT ON COLUMN bank_account_approval_limit.grantee_kind IS
    'MEMBERSHIP_ROLE (role_code on the owning unit) | GLOBAL_ROLE (global role code, SPECIAL accounts) | USER (grantee_user_id) | ALL_MEMBERS.';
COMMENT ON COLUMN bank_account_approval_limit.limit_amount IS
    'Whole-aUEC ceiling (>= 0) up to which the tier may request without owner approval; NUMERIC(19,4) per ADR-0002.';

-- ---------------------------------------------------------------------
-- 2 + 3. Transfer requests and two-step owner approval on the request
-- ---------------------------------------------------------------------
ALTER TABLE bank_booking_request
    ADD COLUMN target_account_id               UUID REFERENCES bank_account (id),
    ADD COLUMN requires_owner_approval         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN applicable_limit                NUMERIC(19, 4),
    ADD COLUMN owner_approval_granted          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN owner_approval_granted_by       UUID REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN owner_approval_granted_by_handle VARCHAR(255),
    ADD COLUMN owner_approval_granted_at       TIMESTAMP WITH TIME ZONE;

-- Widen the type CHECK to admit user-initiated transfer requests (REQ-BANK-040).
ALTER TABLE bank_booking_request
    DROP CONSTRAINT chk_bank_booking_request_type;
ALTER TABLE bank_booking_request
    ADD CONSTRAINT chk_bank_booking_request_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER'));

-- The "Fremde Anträge" tab and the close-account guard read requests per account;
-- the existing idx_bank_booking_request_account already covers (account_id).

COMMENT ON COLUMN bank_booking_request.target_account_id IS
    'Destination account for a TRANSFER request (REQ-BANK-040); NULL for DEPOSIT/WITHDRAWAL.';
COMMENT ON COLUMN bank_booking_request.requires_owner_approval IS
    'Snapshot at creation (REQ-BANK-041): TRUE iff amount exceeded the requester''s applicable approval limit; gates the confirmation checkbox.';
COMMENT ON COLUMN bank_booking_request.applicable_limit IS
    'The requester''s resolved approval limit at creation; NULL = unlimited (no approval needed).';
COMMENT ON COLUMN bank_booking_request.owner_approval_granted IS
    'TRUE once the responsible holder granted approval in-app (REQ-BANK-041); pre-fills the bank employee''s confirmation checkbox.';
COMMENT ON COLUMN bank_booking_request.owner_approval_granted_by IS
    'Responsible holder who granted in-app approval; loose reference (ON DELETE SET NULL) with a denormalized handle snapshot.';
