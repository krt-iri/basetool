-- =====================================================================
-- V174 - notification rule: new Discord registration pending (epic #720, REQ-NOTIF-012)
-- =====================================================================
-- Why: when a new Discord user lands PENDING, every admin must be notified so they
-- can review and approve (REQ-NOTIF-012). Reuses the data-driven rule engine
-- (epic #622): a DISCORD_REGISTRATION_PENDING event is mapped to a notification
-- whose sole recipients are the admins (a ROLE selector with role_code = 'ADMIN'),
-- mirroring the V160/V161 seed pattern. No PII (no Discord id) rides the event.

INSERT INTO notification_rule
    (id, event_type, notification_type, description, enabled, exclude_actor)
VALUES
    ('62200000-0000-0000-0000-000000000005',
     'DISCORD_REGISTRATION_PENDING',
     'DISCORD_REGISTRATION_PENDING',
     'Default: notify all admins when a new Discord user registers and is awaiting approval.',
     TRUE,
     FALSE);

INSERT INTO notification_rule_selector
    (id, rule_id, kind, role_code)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000005', 'ROLE', 'ADMIN');
