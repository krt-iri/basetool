-- =====================================================================
-- V161 - Bank org-unit features (epic #666, F2): decision notifications
-- =====================================================================
-- Why: when a bank employee confirms or rejects an org-unit officer/lead's
-- booking request (V159), the requester must learn the outcome (REQ-BANK-026).
-- This reuses the data-driven notification rule engine (epic #622, REQ-NOTIF-007,
-- ADR-0015) like the create notification (V160): the two decision events are
-- each mapped to a same-named notification whose sole recipient is the requesting
-- officer/lead — resolved by the new EVENT_RECIPIENT selector kind, which reads
-- the directed recipient off the event (NotificationEvent#contextRecipientSub),
-- mirroring how ACCOUNT_GRANT reads the account (ADR-0022). The deciding employee
-- is the event actor and is excluded (exclude_actor = TRUE). Like the other rules
-- these are admin-editable and -deletable at runtime; neither type column carries
-- a CHECK (the @Enumerated(STRING) mappings are the source of truth).
--
-- Rollback: DELETE the two rules (their selectors cascade via the V156 FK).

INSERT INTO notification_rule
    (id, event_type, notification_type, description, enabled, exclude_actor)
VALUES
    ('62200000-0000-0000-0000-000000000003',
     'BANK_BOOKING_REQUEST_CONFIRMED',
     'BANK_BOOKING_REQUEST_CONFIRMED',
     'Default: notify the requesting officer/lead when a bank employee confirms their booking request.',
     TRUE,
     TRUE),
    ('62200000-0000-0000-0000-000000000004',
     'BANK_BOOKING_REQUEST_REJECTED',
     'BANK_BOOKING_REQUEST_REJECTED',
     'Default: notify the requesting officer/lead when a bank employee rejects their booking request.',
     TRUE,
     TRUE);

-- EVENT_RECIPIENT resolves the requester carried by the event; it populates no
-- selector columns of its own.
INSERT INTO notification_rule_selector
    (id, rule_id, kind)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000003', 'EVENT_RECIPIENT'),
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000004', 'EVENT_RECIPIENT');
