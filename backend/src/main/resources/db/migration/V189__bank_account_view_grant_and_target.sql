-- =====================================================================
-- V189 - Bank: account responsibility, configurable balance visibility
--        and the balance target (REQ-BANK-034/-035/-036/-037/-038)
-- =====================================================================
-- Why: the org-unit side of the bank gets two new, user-managed dimensions
-- on top of the org-unit-blind bank authorization (REQ-BANK-008, ADR-0011),
-- both mediated exclusively by the OrgUnitBankAccessService seam (ADR-0020):
--
--  1. balance_target - an optional aspirational fill goal per account, shown
--     with progress to everyone who may view the balance. Set by the account's
--     derived responsible holder (Staffelleiter / SK-Leiter / Bereichsleiter /
--     OL member / Profit-Bereichsleiter) or by bank staff with access. It lives
--     on bank_account (not on a side table): the target and the rename/close
--     lifecycle are both infrequent management/holder actions, so sharing the
--     row's @Version is acceptable and a concurrent edit simply surfaces a 409.
--
--  2. bank_account_view_grant - additional, holder-configured read access to an
--     account's balance + read-only detail (history + statement). The row's
--     EXISTENCE grants view access to the named audience; there are no flags.
--     The audience is polymorphic (grantee_kind):
--       MEMBERSHIP_ROLE - members holding role_code on the owning org unit
--                         (squadron/SK/Bereich sub-ranks; org-unit accounts)
--       GLOBAL_ROLE     - holders of a global role code (e.g. OFFICER; used for
--                         SPECIAL accounts which have no owning org unit)
--       USER            - one named user (grantee_user_id)
--       ALL_MEMBERS     - every member of the owning unit (org-unit accounts) /
--                         every KRT member (SPECIAL)
--     This is distinct from bank_account_grant (V152), which is the bank-staff
--     capability grant and stays untouched.
--
-- Rollback: ALTER TABLE bank_account DROP COLUMN balance_target;
--           DROP TABLE bank_account_view_grant. (Leaf table.)

ALTER TABLE bank_account
    ADD COLUMN balance_target NUMERIC(19, 4);

COMMENT ON COLUMN bank_account.balance_target IS
    'Optional aspirational balance goal (REQ-BANK-036); NULL = no target. NUMERIC(19,4) per ADR-0002.';

CREATE TABLE bank_account_view_grant (
    id              UUID PRIMARY KEY,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    account_id      UUID                     NOT NULL REFERENCES bank_account (id) ON DELETE CASCADE,
    grantee_kind    VARCHAR(16)              NOT NULL,
    role_code       VARCHAR(64),
    grantee_user_id UUID                     REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_bank_view_grant_kind
        CHECK (grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE', 'USER', 'ALL_MEMBERS')),
    -- The populated columns must match the grantee kind.
    CONSTRAINT chk_bank_view_grant_payload
        CHECK (
            (grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE')
                AND role_code IS NOT NULL AND grantee_user_id IS NULL)
            OR (grantee_kind = 'USER'
                AND grantee_user_id IS NOT NULL AND role_code IS NULL)
            OR (grantee_kind = 'ALL_MEMBERS'
                AND role_code IS NULL AND grantee_user_id IS NULL)
        )
);

-- One grant per (account, role-kind, role): toggling a role bucket is idempotent.
CREATE UNIQUE INDEX uq_bank_view_grant_role
    ON bank_account_view_grant (account_id, grantee_kind, role_code)
    WHERE grantee_kind IN ('MEMBERSHIP_ROLE', 'GLOBAL_ROLE');

-- One grant per (account, user): granting the same user twice is idempotent.
CREATE UNIQUE INDEX uq_bank_view_grant_user
    ON bank_account_view_grant (account_id, grantee_user_id)
    WHERE grantee_kind = 'USER';

-- At most one all-members grant per account.
CREATE UNIQUE INDEX uq_bank_view_grant_all_members
    ON bank_account_view_grant (account_id)
    WHERE grantee_kind = 'ALL_MEMBERS';

-- Visibility is resolved per account ("who else may view this account?").
CREATE INDEX idx_bank_view_grant_account
    ON bank_account_view_grant (account_id);

COMMENT ON TABLE bank_account_view_grant IS
    'Holder-configured additional read access to a bank account''s balance and read-only detail (REQ-BANK-035/-038). Row existence = view access for the named audience; distinct from the bank-staff capability grants (bank_account_grant).';
COMMENT ON COLUMN bank_account_view_grant.grantee_kind IS
    'MEMBERSHIP_ROLE (role_code on the owning unit) | GLOBAL_ROLE (global role code, SPECIAL accounts) | USER (grantee_user_id) | ALL_MEMBERS.';
COMMENT ON COLUMN bank_account_view_grant.role_code IS
    'A MembershipRole name (MEMBERSHIP_ROLE) or a global role code (GLOBAL_ROLE); NULL for USER / ALL_MEMBERS.';
