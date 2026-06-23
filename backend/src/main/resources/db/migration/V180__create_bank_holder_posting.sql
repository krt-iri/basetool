-- =====================================================================
-- V180 - Kartell bank: decoupled holder ledger (ADR-0039, REQ-BANK-003/-004)
-- =====================================================================
-- Why: holder custody is no longer a per-(account, holder) sub-balance of the
-- account ledger. It becomes its own GLOBAL dimension in a second append-only
-- ledger, decoupled from accounts: the money booked on an account and the money
-- a player physically holds are tracked in parallel (a custodian credited via
-- Staffel A may pay out a request booked against Staffel B). A holder balance is
-- SUM(amount) over THIS table across the whole bank and may be negative
-- (REQ-BANK-006 - holders front their own money, reconciled later by a
-- HOLDER_TRANSFER Umbuchung). Like bank_posting this is an insert-only event log
-- (external_sync_report style): no version, no updated_at; corrections are
-- REVERSAL rows.
--
-- Per transaction type (co-recorded with bank_posting account legs):
--   DEPOSIT/WITHDRAWAL  -> one holder leg (sign matches the account leg)
--   TRANSFER            -> two holder legs summing to zero (custody moves too)
--   HOLDER_TRANSFER     -> two holder legs summing to zero, NO account leg
--   WIPE_RESET          -> one negative holder leg per non-zero global balance
--   REVERSAL            -> negated mirror of the original's holder legs
--
-- Backfill: every existing bank_posting row carried (account, holder, amount) as
-- one coupled leg. We copy its (transaction, holder, amount) into the new holder
-- ledger so each holder's global balance is preserved exactly; V181 then drops
-- bank_posting.holder_id. No existing row is mutated - append-only is preserved.
--
-- Rollback: DROP TABLE bank_holder_posting.

CREATE TABLE bank_holder_posting (
    id             UUID PRIMARY KEY,
    transaction_id UUID                     NOT NULL REFERENCES bank_transaction (id),
    holder_id      UUID                     NOT NULL REFERENCES bank_holder (id) ON DELETE RESTRICT,
    amount         NUMERIC(19, 4)           NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_bank_holder_posting_amount_nonzero CHECK (amount <> 0)
);

-- Global holder balance: SUM(amount) per holder, range-filtered by created_at.
CREATE INDEX idx_bank_holder_posting_holder_created
    ON bank_holder_posting (holder_id, created_at);

-- Leg lookup per transaction (reversal mirroring, per-booking holder annotation).
CREATE INDEX idx_bank_holder_posting_transaction
    ON bank_holder_posting (transaction_id);

-- Backfill from the coupled legs (preserves every holder's global balance).
INSERT INTO bank_holder_posting (id, transaction_id, holder_id, amount, created_at)
SELECT gen_random_uuid(), p.transaction_id, p.holder_id, p.amount, p.created_at
FROM bank_posting p;

-- Admit the new HOLDER_TRANSFER transaction type (holder->holder Umbuchung, REQ-BANK-031).
ALTER TABLE bank_transaction
    DROP CONSTRAINT chk_bank_transaction_type;
ALTER TABLE bank_transaction
    ADD CONSTRAINT chk_bank_transaction_type
        CHECK (type IN
               ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'HOLDER_TRANSFER', 'WIPE_RESET', 'REVERSAL'));

COMMENT ON TABLE bank_holder_posting IS
    'Append-only holder-custody ledger (ADR-0039, REQ-BANK-003). Decoupled from accounts; a holder''s global balance is SUM(amount) over this table and may be negative (REQ-BANK-006).';
COMMENT ON COLUMN bank_holder_posting.amount IS
    'Signed whole-aUEC amount, NUMERIC(19,4) per ADR-0002; never zero.';
