-- =====================================================================
-- V160 - Bank org-unit features (epic #666, F2): notification rule (UC2)
-- =====================================================================
-- Why: when an org-unit officer/lead raises a confirm-before-post booking
-- request (V159), the bank staff who can act on it must be told (REQ-BANK-026).
-- This reuses the data-driven notification rule engine (epic #622, REQ-NOTIF-007,
-- ADR-0015) rather than hardcoding recipients: a BANK_BOOKING_REQUEST_CREATED
-- event is mapped to a BANK_BOOKING_REQUEST_CREATED notification whose recipients
-- are
--   * the bank management (ROLE selector, role code BANK_MANAGEMENT), and
--   * every employee granted on the target account (ACCOUNT_GRANT selector — a new
--     selector kind that reads the account from the event, mirroring how
--     ORG_RELATIVE_ROLE reads the org unit; ADR-0022).
-- The requesting actor is excluded (exclude_actor = TRUE). Like UC1, this rule is
-- admin-editable and -deletable at runtime; neither type column carries a CHECK
-- (the @Enumerated(STRING) mappings are the source of truth, V154/V156 precedent).
--
-- Rollback: DELETE the rule (its selectors cascade via the V156 FK).

INSERT INTO notification_rule
    (id, event_type, notification_type, description, enabled, exclude_actor)
VALUES
    ('62200000-0000-0000-0000-000000000002',
     'BANK_BOOKING_REQUEST_CREATED',
     'BANK_BOOKING_REQUEST_CREATED',
     'Default: notify the bank management and the employees granted on the account when an org-unit officer/lead raises a deposit/withdrawal booking request.',
     TRUE,
     TRUE);

INSERT INTO notification_rule_selector
    (id, rule_id, kind, role_code)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000002', 'ROLE', 'BANK_MANAGEMENT');

-- ACCOUNT_GRANT resolves the granted employees from the account carried by the
-- event; it populates no selector columns of its own.
INSERT INTO notification_rule_selector
    (id, rule_id, kind)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000002', 'ACCOUNT_GRANT');
