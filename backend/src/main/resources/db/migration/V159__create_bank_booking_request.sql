-- =====================================================================
-- V159 - Bank org-unit features (epic #666, F2): bank_booking_request
-- =====================================================================
-- Why: org-unit officers/leads raise confirm-before-post deposit/withdrawal
-- requests against their org unit's account (REQ-BANK-022/-023). A request is
-- a MUTABLE, off-ledger aggregate (ADR-0021) — the opposite of the append-only
-- ledger (bank_transaction/bank_posting, ADR-0010): it carries the standard
-- optimistic-locking version column and moves NO money while PENDING. Only a
-- bank employee's confirmation books a real transaction onto the ledger and
-- flips the request to CONFIRMED, recording the holder (deposit → who received
-- the money; withdrawal → who paid it out) and linking the resulting
-- transaction. REJECTED/CANCELLED/PENDING rows never carry a holder or a
-- resulting transaction (chk_..._confirmed_refs).
--
-- type/status carry CHECK constraints because the sets are stable (you can only
-- deposit or withdraw via a request; a request reaches exactly one terminal
-- state) — mirroring bank_account.type/status (V150), unlike the open-ended
-- bank_audit_event.event_type (V154). requested_by/decided_by are plain UUIDs
-- with ON DELETE SET NULL plus a denormalized handle snapshot (bank_audit_event
-- V154 precedent) so the row and the bank-staff queue survive a user deletion.
--
-- Rollback: DROP TABLE bank_booking_request. Leaf table.

CREATE TABLE bank_booking_request (
    id                       UUID PRIMARY KEY,
    version                  BIGINT                   NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    account_id               UUID                     NOT NULL REFERENCES bank_account (id),
    type                     VARCHAR(16)              NOT NULL,
    amount                   NUMERIC(19, 4)           NOT NULL,
    note                     VARCHAR(500),
    status                   VARCHAR(16)              NOT NULL DEFAULT 'PENDING',
    requested_by             UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    requester_handle         VARCHAR(255)             NOT NULL,
    holder_id                UUID                     REFERENCES bank_holder (id),
    resulting_transaction_id UUID                     REFERENCES bank_transaction (id),
    decided_by               UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    decider_handle           VARCHAR(255),
    decided_at               TIMESTAMP WITH TIME ZONE,
    reject_reason            VARCHAR(500),
    CONSTRAINT chk_bank_booking_request_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_bank_booking_request_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_bank_booking_request_amount_positive
        CHECK (amount > 0),
    -- A confirmed request books the ledger and must carry both the recorded holder
    -- and the resulting transaction; every other state carries neither (off-ledger).
    CONSTRAINT chk_bank_booking_request_confirmed_refs
        CHECK (
            (status = 'CONFIRMED' AND holder_id IS NOT NULL AND resulting_transaction_id IS NOT NULL)
            OR (status <> 'CONFIRMED' AND holder_id IS NULL AND resulting_transaction_id IS NULL)
        )
);

-- The bank-staff confirmation queue lists PENDING requests, optionally scoped to
-- the accounts an employee is granted on.
CREATE INDEX idx_bank_booking_request_status_account
    ON bank_booking_request (status, account_id);

-- The requester's "my requests" list reads their own rows, most-recent first.
CREATE INDEX idx_bank_booking_request_requester
    ON bank_booking_request (requested_by, created_at DESC);

-- The close-account guard probes for any open PENDING request on the account.
CREATE INDEX idx_bank_booking_request_account
    ON bank_booking_request (account_id);

COMMENT ON TABLE bank_booking_request IS
    'Confirm-before-post deposit/withdrawal requests by org-unit officers/leads (epic #666, REQ-BANK-022/-023). Mutable, off-ledger; only a bank employee''s confirmation books a real ledger transaction.';
COMMENT ON COLUMN bank_booking_request.requested_by IS
    'Requesting officer/lead app_user id; loose reference (ON DELETE SET NULL), per-user isolation key for the "my requests" list.';
COMMENT ON COLUMN bank_booking_request.holder_id IS
    'Holder recorded by the bank employee at confirmation; NULL until CONFIRMED (deposit → receiver, withdrawal → payer).';
COMMENT ON COLUMN bank_booking_request.resulting_transaction_id IS
    'The ledger transaction booked on confirmation; NULL until CONFIRMED.';
