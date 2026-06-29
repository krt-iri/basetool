-- =====================================================================
-- V196 - Split deposit on bank_booking_request (REQ-BANK-043)
-- =====================================================================
-- Why: a deposit (direct booking AND confirm-before-post request) may now
-- distribute a percentage of the gross evenly across all active squadron
-- accounts, crediting the named account only the remainder (REQ-BANK-043,
-- amends REQ-BANK-004/-005/-042). For the request variant the percentage is
-- SNAPSHOTTED on the off-ledger request at creation; the concrete per-account
-- legs are (re)computed against the squadron-account set that is active at
-- confirmation time, exactly like the approval-limit snapshot of REQ-BANK-041.
--
-- split_enabled defaults FALSE so every existing PENDING request keeps its
-- single-account semantics with no backfill. split_percent is NULL unless the
-- split is enabled. The split only ever applies to a DEPOSIT (money entering
-- the bank): a withdrawal/transfer never carries it — pinned by the CHECK
-- below alongside the whole-percent 1..100 range (the app additionally enforces
-- a whole-number percent via @WholeNumber).
--
-- Rollback: ALTER TABLE bank_booking_request DROP COLUMN split_percent,
--           DROP COLUMN split_enabled (drops the CHECK with the columns).

ALTER TABLE bank_booking_request
    ADD COLUMN split_enabled BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN split_percent NUMERIC(5, 2);

-- A split is DEPOSIT-only; when enabled it carries a whole-percent in [1, 100],
-- otherwise it carries no percent at all.
ALTER TABLE bank_booking_request
    ADD CONSTRAINT chk_bank_booking_request_split
        CHECK (
            (split_enabled = FALSE AND split_percent IS NULL)
            OR (split_enabled = TRUE
                AND type = 'DEPOSIT'
                AND split_percent IS NOT NULL
                AND split_percent >= 1
                AND split_percent <= 100)
        );

COMMENT ON COLUMN bank_booking_request.split_enabled IS
    'REQ-BANK-043: whether this DEPOSIT request distributes split_percent of the gross evenly across all active squadron accounts (the named account is credited the remainder). DEPOSIT-only.';
COMMENT ON COLUMN bank_booking_request.split_percent IS
    'REQ-BANK-043: the whole-percent (1..100) of the gross distributed across squadron accounts; NULL unless split_enabled. Snapshotted at creation; the concrete legs are resolved against the active squadron accounts at confirmation.';
