-- =====================================================================
-- V182 - Kartell bank: bank_holder.role_managed (ADR-0040, REQ-BANK-029)
-- =====================================================================
-- Why: all bank staff (ROLE_BANK_EMPLOYEE / ROLE_BANK_MANAGEMENT) are
-- auto-registered as holders, reconciled at the role-sync points. role_managed
-- marks a holder that exists BECAUSE of a bank role: when its user loses all
-- bank roles the reconcile auto-deactivates it (the balance survives and must be
-- reconciled to zero). Manually registered custodians (role_managed = FALSE, the
-- default for every pre-existing row) are NEVER touched by the reconcile.
--
-- Existing rows default to FALSE (manual); the next reconcile sweep flags the
-- holders whose user currently holds a bank role.
--
-- Rollback: ALTER TABLE bank_holder DROP COLUMN role_managed.

ALTER TABLE bank_holder
    ADD COLUMN role_managed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN bank_holder.role_managed IS
    'TRUE = holder auto-created from a bank role (ADR-0040); auto-deactivated when all bank roles are lost. FALSE = manually registered custodian, never auto-touched.';
