-- =====================================================================
-- V196 - Kartell bank: bank_transaction counterparty (REQ-BANK-043)
-- =====================================================================
-- Why: a deposit/withdrawal so far records only the HOLDER — the bank
-- custodian who physically received the money (deposit) or paid it out
-- (withdrawal). It does NOT record the external party on the other side:
-- WHO handed the money in (the Einzahler) or WHO received the payout (the
-- Empfaenger), and which org unit they belong to. The owner wants every
-- deposit/withdrawal to capture this counterparty so the account history,
-- the Kontoauszug PDF and the admin audit log show "von wem / an wen" the
-- payment went.
--
-- The counterparty is a distinct dimension from the holder: it is the
-- member on the far side of the booking, optionally with the org unit they
-- belong to. It is stored on the transaction header (exactly one per
-- deposit/withdrawal, set once at insert, never updated — the append-only
-- contract of REQ-BANK-004 holds). It is recorded only for DEPOSIT and
-- WITHDRAWAL; TRANSFER / HOLDER_TRANSFER / REVERSAL / WIPE_RESET leave it
-- NULL (their from/to account + holder are already on the legs).
--
-- A deletion-proof handle / org-unit-name snapshot is stored alongside the
-- FKs (mirrors bank_audit_event.actor_handle and bank_holder.handle) so the
-- ledger stays readable after the user or org unit is deleted. The org-unit
-- FK references org_unit(id) — never squadron_id (the bank already links
-- org_unit since V168). All four columns default to NULL on existing rows.
--
-- Rollback: ALTER TABLE bank_transaction
--   DROP COLUMN counterparty_user_id, DROP COLUMN counterparty_handle,
--   DROP COLUMN counterparty_org_unit_id, DROP COLUMN counterparty_org_unit_name.

ALTER TABLE bank_transaction
    ADD COLUMN counterparty_user_id      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN counterparty_handle       VARCHAR(255),
    ADD COLUMN counterparty_org_unit_id  UUID REFERENCES org_unit (id) ON DELETE SET NULL,
    ADD COLUMN counterparty_org_unit_name VARCHAR(255);

-- A handle snapshot exists iff a counterparty user is named; an org unit
-- (id + name) only ever accompanies a counterparty user.
ALTER TABLE bank_transaction
    ADD CONSTRAINT chk_bank_transaction_counterparty
        CHECK (
            (counterparty_user_id IS NULL) = (counterparty_handle IS NULL)
            AND (counterparty_org_unit_id IS NULL) = (counterparty_org_unit_name IS NULL)
            AND (counterparty_org_unit_id IS NULL OR counterparty_user_id IS NOT NULL)
        );

COMMENT ON COLUMN bank_transaction.counterparty_user_id IS
    'The member on the far side of a DEPOSIT (Einzahler) / WITHDRAWAL (Empfaenger), FK app_user ON DELETE SET NULL; NULL for transfers/holder-transfers/reversal/wipe and for bookings without a recorded counterparty (REQ-BANK-043).';
COMMENT ON COLUMN bank_transaction.counterparty_handle IS
    'Deletion-proof handle snapshot of counterparty_user_id (mirrors bank_audit_event.actor_handle); NULL exactly when counterparty_user_id is NULL.';
COMMENT ON COLUMN bank_transaction.counterparty_org_unit_id IS
    'Optional org unit the counterparty belongs to, picked from their memberships at booking time; FK org_unit ON DELETE SET NULL, only set together with a counterparty user (REQ-BANK-043).';
COMMENT ON COLUMN bank_transaction.counterparty_org_unit_name IS
    'Deletion-proof name snapshot of counterparty_org_unit_id; NULL exactly when counterparty_org_unit_id is NULL.';
