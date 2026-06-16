-- =====================================================================
-- V156 - Notification system (epic #622, Phase 3): rule engine + UC1 seed
-- =====================================================================
-- Why: notification recipients are data-driven, not hardcoded (REQ-NOTIF-007).
-- A notification_rule maps an event_type to the notification_type to produce
-- and owns a set of notification_rule_selector rows that resolve recipients
-- (SPECIFIC_USER / ROLE / ORG_RELATIVE_ROLE). Admins create, edit, toggle and
-- delete rules at runtime, which is what makes new use cases pluggable without
-- a redeploy. Both type columns are VARCHAR with no CHECK (the @Enumerated
-- mapping is the source of truth, V154 precedent) so the enums grow freely.
--
-- The selector is a child of the rule (FK ON DELETE CASCADE + JPA orphan
-- removal); which columns are populated depends on `kind`.
--
-- The first use case (UC1) is seeded as an admin-editable/-deletable default
-- rule: when a job order is created, notify the officers of the responsible
-- squadron / the leads of the responsible special command, the logisticians of
-- that responsible unit, and the global admins; the creating actor is excluded.
--
-- Rollback: DROP TABLE notification_rule_selector; DROP TABLE notification_rule.

CREATE TABLE notification_rule (
    id                UUID PRIMARY KEY,
    version           BIGINT                   NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    event_type        VARCHAR(64)              NOT NULL,
    notification_type VARCHAR(64)              NOT NULL,
    description       VARCHAR(255),
    enabled           BOOLEAN                  NOT NULL DEFAULT TRUE,
    exclude_actor     BOOLEAN                  NOT NULL DEFAULT TRUE
);

CREATE TABLE notification_rule_selector (
    id                UUID PRIMARY KEY,
    version           BIGINT                   NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    rule_id           UUID                     NOT NULL,
    kind              VARCHAR(32)              NOT NULL,
    user_sub          UUID,
    role_code         VARCHAR(64),
    org_relative_role VARCHAR(32),
    context_role      VARCHAR(32),
    CONSTRAINT fk_notification_rule_selector_rule
        FOREIGN KEY (rule_id) REFERENCES notification_rule (id) ON DELETE CASCADE
);

-- The engine looks up enabled rules by event type on every event.
CREATE INDEX idx_notification_rule_event_type
    ON notification_rule (event_type)
    WHERE enabled = TRUE;

-- The cascade and the rule-detail load both traverse rule_id.
CREATE INDEX idx_notification_rule_selector_rule
    ON notification_rule_selector (rule_id);

COMMENT ON TABLE notification_rule IS
    'Admin-managed rule mapping an event type to a notification type and its recipient selectors (epic #622, REQ-NOTIF-007).';
COMMENT ON TABLE notification_rule_selector IS
    'One recipient selector of a notification_rule; populated columns depend on kind (epic #622, REQ-NOTIF-007).';

-- ---------------------------------------------------------------------
-- UC1 seed: notify on JOB_ORDER_CREATED. Admin-editable and -deletable.
-- ---------------------------------------------------------------------
INSERT INTO notification_rule
    (id, event_type, notification_type, description, enabled, exclude_actor)
VALUES
    ('62200000-0000-0000-0000-000000000001',
     'JOB_ORDER_CREATED',
     'JOB_ORDER_CREATED',
     'Default: notify officers / leads and logisticians of the responsible org unit plus admins when a new job order is created.',
     TRUE,
     TRUE);

INSERT INTO notification_rule_selector
    (id, rule_id, kind, org_relative_role, context_role)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000001', 'ORG_RELATIVE_ROLE', 'OFFICER', 'RESPONSIBLE'),
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000001', 'ORG_RELATIVE_ROLE', 'LEAD', 'RESPONSIBLE'),
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000001', 'ORG_RELATIVE_ROLE', 'LOGISTICIAN', 'RESPONSIBLE');

INSERT INTO notification_rule_selector
    (id, rule_id, kind, role_code)
VALUES
    (gen_random_uuid(), '62200000-0000-0000-0000-000000000001', 'ROLE', 'ADMIN');
