-- =====================================================================
-- V183 - Kartell bank: bank_transaction.transfer_fee (ADR-0041, REQ-BANK-033)
-- =====================================================================
-- Why: Star Citizen charges an in-game fee on every aUEC transfer a holder
-- actively initiates. The bank now factors that fee into the transactions where
-- a holder sends money (WITHDRAWAL, account-to-account TRANSFER with a holder
-- change, holder-to-holder HOLDER_TRANSFER) so the bank staff are not out of
-- pocket: the entered amount is the gross that physically leaves the source
-- (debited in full), the fee is carved out and recorded here, and the
-- destination is credited the NET (gross - fee) — i.e. the money that actually
-- arrives is smaller (same rate as the operation payout, system_setting
-- operation.transfer_fee_rate).
--
-- Consequence (ADR-0041): a fee-bearing TRANSFER / HOLDER_TRANSFER no longer
-- nets to zero across its legs — it nets to -transfer_fee (real money lost to
-- the game). The REQ-BANK-020 integrity checks are widened to expect a leg sum
-- of -transfer_fee instead of 0; the REVERSAL mirror invariant is unaffected
-- (a reversal negates the actual recorded legs). DEPOSIT and WIPE_RESET carry no
-- fee (0): a depositor bears their own fee, a wipe books exact balances.
--
-- Existing rows and every non-fee transaction default to 0.
--
-- Rollback: ALTER TABLE bank_transaction DROP COLUMN transfer_fee.

ALTER TABLE bank_transaction
    ADD COLUMN transfer_fee NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE bank_transaction
    ADD CONSTRAINT chk_bank_transaction_transfer_fee_non_negative
        CHECK (transfer_fee >= 0);

COMMENT ON COLUMN bank_transaction.transfer_fee IS
    'In-game aUEC transfer fee carved out of the gross sent amount (ADR-0041, REQ-BANK-033); >= 0, and 0 for DEPOSIT/WIPE_RESET/REVERSAL and same-holder transfers. The destination leg is credited gross - transfer_fee.';
