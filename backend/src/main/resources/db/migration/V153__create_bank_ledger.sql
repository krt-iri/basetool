-- =====================================================================
-- V153 - Kartell bank (epic #556, Phase 1): append-only double-entry ledger
-- =====================================================================
-- Why: all value movements are recorded as a transaction header plus 1..n
-- signed postings (ADR-0010, REQ-BANK-004). Both tables are insert-only event
-- logs in the external_sync_report style: no version column, no updated_at -
-- rows are NEVER updated or deleted; corrections are REVERSAL transactions
-- referencing the original. Balances are computed on read (SUM over
-- bank_posting), backed by the composite indexes below (REQ-BANK-020).
--
-- Every posting names exactly ONE holder (REQ-BANK-003): the player whose
-- physical stash changes. ON DELETE RESTRICT keeps holders alive while
-- referenced. The transaction type carries a CHECK (small fixed set defined
-- by the spec); a REVERSAL must - and only a REVERSAL may - reference the
-- transaction it reverses, and a transaction can be reversed at most once
-- (UNIQUE on reversed_transaction_id).
--
-- Rollback: DROP TABLE bank_posting; DROP TABLE bank_transaction.

CREATE TABLE bank_transaction (
    id                      UUID PRIMARY KEY,
    type                    VARCHAR(16)              NOT NULL,
    initiated_by            UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    note                    VARCHAR(500),
    reversed_transaction_id UUID                     REFERENCES bank_transaction (id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_bank_transaction_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'WIPE_RESET', 'REVERSAL')),
    CONSTRAINT uq_bank_transaction_reversed UNIQUE (reversed_transaction_id),
    CONSTRAINT chk_bank_transaction_reversal_ref
        CHECK ((type = 'REVERSAL') = (reversed_transaction_id IS NOT NULL))
);

CREATE TABLE bank_posting (
    id             UUID PRIMARY KEY,
    transaction_id UUID                     NOT NULL REFERENCES bank_transaction (id),
    account_id     UUID                     NOT NULL REFERENCES bank_account (id),
    holder_id      UUID                     NOT NULL REFERENCES bank_holder (id) ON DELETE RESTRICT,
    amount         NUMERIC(19, 4)           NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_bank_posting_amount_nonzero CHECK (amount <> 0)
);

-- Balance + statement/period queries: SUM(amount) per account, range-filtered
-- by created_at (REQ-BANK-020).
CREATE INDEX idx_bank_posting_account_created
    ON bank_posting (account_id, created_at);

-- Leg lookup per transaction (counter-account resolution, reversal mirroring).
CREATE INDEX idx_bank_posting_transaction
    ON bank_posting (transaction_id);

-- Holder distribution: SUM(amount) per (account, holder) (REQ-BANK-003) and
-- the per-holder no-overdraft guard (REQ-BANK-006).
CREATE INDEX idx_bank_posting_account_holder
    ON bank_posting (account_id, holder_id);

COMMENT ON TABLE bank_transaction IS
    'Append-only bank transaction headers (epic #556, ADR-0010). Insert-only: corrections are REVERSAL rows, never updates.';
COMMENT ON TABLE bank_posting IS
    'Signed double-entry ledger legs (epic #556, ADR-0010). Every leg names exactly one holder (REQ-BANK-003); account and holder balances are SUMs over this table.';
COMMENT ON COLUMN bank_posting.amount IS
    'Signed whole-aUEC amount, NUMERIC(19,4) per ADR-0002; never zero.';
