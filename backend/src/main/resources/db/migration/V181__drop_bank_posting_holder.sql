-- =====================================================================
-- V181 - Kartell bank: bank_posting becomes account-only (ADR-0039)
-- =====================================================================
-- Why: with the holder dimension now in its own ledger (V180,
-- bank_holder_posting), a bank_posting row carries ONLY the account dimension
-- (account, signed amount). The holder_id column, its FK and the per-(account,
-- holder) composite index are dropped - the per-account holder distribution they
-- supported is removed (REQ-BANK-003/-014/-015). The per-booking holder
-- annotation on an account's history is now derived via the shared
-- bank_transaction (the sibling holder leg), not this column.
--
-- The data is not lost: V180 copied every (transaction, holder, amount) into
-- bank_holder_posting first. No row is mutated here - only the column/index are
-- dropped (structural change; append-only contract preserved).
--
-- Rollback: re-add bank_posting.holder_id (nullable), the FK and the index, then
-- backfill from bank_holder_posting by matching transaction + amount sign.

DROP INDEX IF EXISTS idx_bank_posting_account_holder;

ALTER TABLE bank_posting
    DROP COLUMN holder_id;

COMMENT ON TABLE bank_posting IS
    'Append-only account-ledger legs (epic #556, ADR-0010/0039). Account-only since V181; account balance is SUM(amount) over this table. The holder dimension lives in bank_holder_posting.';
