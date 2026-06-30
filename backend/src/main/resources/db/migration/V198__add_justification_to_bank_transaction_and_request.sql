-- =====================================================================
-- V198 - Begruendung (justification) on bank transactions + requests (REQ-BANK-045)
-- =====================================================================
-- Why: a withdrawal or transfer (direct bank-staff booking AND confirm-before-post
-- request) now carries an optional free-text Begruendung alongside the existing
-- note. It is a first-class sibling of note: persisted on the append-only ledger
-- header (bank_transaction) and on the off-ledger request (bank_booking_request),
-- carried request -> confirmation, and rendered everywhere note is (booking
-- history, statement + management PDFs). A deposit never captures one.
--
-- The conditional-required rule (mandatory when the debited account type is
-- CARTEL, CARTEL_BANK or SPECIAL; optional for ORG_UNIT / AREA) is enforced in the
-- service layer, not by a DB CHECK: it depends on the source account's type, which
-- is not a column of either table. The column is therefore plain nullable on both,
-- mirroring note. No backfill: every existing row keeps a NULL justification.
--
-- Rollback: ALTER TABLE bank_transaction DROP COLUMN justification;
--           ALTER TABLE bank_booking_request DROP COLUMN justification;

ALTER TABLE bank_transaction
    ADD COLUMN justification VARCHAR(500);

ALTER TABLE bank_booking_request
    ADD COLUMN justification VARCHAR(500);

COMMENT ON COLUMN bank_transaction.justification IS
    'REQ-BANK-045: optional free-text Begruendung for a WITHDRAWAL / TRANSFER, shown in the booking history and on statements; required (service-enforced) when the debited account type mandates a reason (CARTEL, CARTEL_BANK, SPECIAL), NULL otherwise and for deposits.';
COMMENT ON COLUMN bank_booking_request.justification IS
    'REQ-BANK-045: optional free-text Begruendung supplied by the requester for a WITHDRAWAL / TRANSFER request, carried onto the booking on confirmation; required (service-enforced) when the source account type mandates a reason, NULL otherwise and for deposit requests.';
