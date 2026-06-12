-- =====================================================================
-- V152 - Kartell bank (epic #556, Phase 1): bank_account_grant
-- =====================================================================
-- Why: fine-grained bank permissions are app-managed grants, not Keycloak
-- roles (ADR-0011, REQ-BANK-009): one row per (user, account) with three
-- independent capability flags. The EXISTENCE of the row gives view access
-- (a row with all flags false is view-only), the flags gate
-- deposit/withdraw/transfer per account. Composite PK like
-- org_unit_membership (V95); optimistic locking via the standard version
-- column so two managers editing the same grant surface a 409 instead of
-- silently losing a write.
--
-- ON DELETE CASCADE on user_id: a deleted user cannot exercise grants and
-- the audit trail (V154) keeps the history. granted_by is SET NULL - the
-- grant survives the granting manager's deletion.
--
-- Rollback: DROP TABLE bank_account_grant. Leaf table.

CREATE TABLE bank_account_grant (
    user_id      UUID    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    account_id   UUID    NOT NULL REFERENCES bank_account (id) ON DELETE CASCADE,
    version      BIGINT  NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    can_deposit  BOOLEAN NOT NULL DEFAULT FALSE,
    can_withdraw BOOLEAN NOT NULL DEFAULT FALSE,
    can_transfer BOOLEAN NOT NULL DEFAULT FALSE,
    granted_by   UUID    REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, account_id)
);

-- The grants UI lists per account ("who may do what on this account?"), so
-- the reverse lookup needs its own index (the PK only covers user-first).
CREATE INDEX idx_bank_account_grant_account
    ON bank_account_grant (account_id);

COMMENT ON TABLE bank_account_grant IS
    'Per-employee per-account bank capabilities (epic #556, REQ-BANK-009). Row existence = view access; flags = booking capabilities.';
COMMENT ON COLUMN bank_account_grant.granted_by IS
    'The manager/admin who created the grant; informational, survives via audit trail when NULLed.';
