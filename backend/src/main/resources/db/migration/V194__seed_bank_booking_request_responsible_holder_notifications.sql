-- =====================================================================
-- V194 - Bank: notify the account's responsible holder on a booking request
-- =====================================================================
-- Why: the account's responsible holder (Kontoverantwortliche, REQ-BANK-034)
-- must be told when a booking request on their account is created or decided
-- (owner request). This reuses the data-driven notification rule engine
-- (epic #622, REQ-NOTIF-007, ADR-0015) rather than hardcoding recipients: the
-- new ACCOUNT_RESPONSIBLE selector resolves the responsible holder(s) from the
-- account carried by the event (NotificationEvent#contextAccountId), mirroring
-- how ACCOUNT_GRANT resolves the granted employees; the org-unit-aware lookup
-- stays inside the OrgUnitBankAccessService seam so the bank stays
-- org-unit-blind (REQ-BANK-008).
--
--   * On CREATE: the responsible holder joins the existing
--     BANK_BOOKING_REQUEST_CREATED rule (account-centric text already fits) —
--     we only add an ACCOUNT_RESPONSIBLE selector to it.
--   * On CONFIRM / REJECT: the existing decision rules speak to the requester
--     ("Dein Antrag ... wurde bestaetigt"), so the holder gets two NEW rules
--     producing the account-centric RESPONSIBLE_CONFIRMED / RESPONSIBLE_REJECTED
--     notification types, each resolved by ACCOUNT_RESPONSIBLE.
--
-- The deciding/creating actor is excluded (exclude_actor = TRUE), so a holder
-- who is also the requester is not notified about their own request, and a
-- holder who confirms/rejects is not notified about their own decision. Like
-- the other rules these are admin-editable and -deletable at runtime; neither
-- type column carries a CHECK (the @Enumerated(STRING) mappings are the source
-- of truth).
--
-- Rollback: DELETE the two new rules + the added selector row (selectors of the
-- new rules cascade via the V156 FK).

-- (1) CREATE: add the responsible holder to the existing create rule.
INSERT INTO notification_rule_selector
    (id, rule_id, kind)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000002', 'ACCOUNT_RESPONSIBLE');

-- (2) CONFIRM / REJECT: new holder-directed rules with account-centric text.
INSERT INTO notification_rule
    (id, event_type, notification_type, description, enabled, exclude_actor)
VALUES
    ('62200000-0000-0000-0000-000000000006',
     'BANK_BOOKING_REQUEST_CONFIRMED',
     'BANK_BOOKING_REQUEST_RESPONSIBLE_CONFIRMED',
     'Default: notify the account''s responsible holder when a bank employee confirms a booking request on their account.',
     TRUE,
     TRUE),
    ('62200000-0000-0000-0000-000000000007',
     'BANK_BOOKING_REQUEST_REJECTED',
     'BANK_BOOKING_REQUEST_RESPONSIBLE_REJECTED',
     'Default: notify the account''s responsible holder when a bank employee rejects a booking request on their account.',
     TRUE,
     TRUE);

-- ACCOUNT_RESPONSIBLE resolves the responsible holder(s) from the account
-- carried by the event; it populates no selector columns of its own.
INSERT INTO notification_rule_selector
    (id, rule_id, kind)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000006', 'ACCOUNT_RESPONSIBLE'),
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000007', 'ACCOUNT_RESPONSIBLE');
