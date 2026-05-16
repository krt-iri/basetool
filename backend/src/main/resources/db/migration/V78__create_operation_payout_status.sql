-- Per-(operation, participant) audit row written by mission managers when they mark
-- a participant as already paid out for their share of an operation. Absence of a
-- row means "not yet paid out" — toggling "paid out" the first time materializes the
-- row, toggling back updates the flag in place rather than deleting it so the audit
-- trail (paid_out_by_user_id / paid_out_at) survives the unset.
--
-- participant_key follows the same opaque format used by /api/v1/operations/{id}/payouts:
-- the participant's real-user UUID stringified, or "guest_<name>" for unauthenticated
-- guests. Real users are intentionally NOT modelled as a hard FK so a guest that later
-- joins Keycloak does not strand its prior paid-out flag, and so the operation roll-up
-- service does not need to fan out into user / guest tables when reconciling status.
--
-- Cascade rules: deleting an operation removes its payout-status rows (this table is
-- conceptually owned by the operation). Deleting the user who pressed the button keeps
-- the row but clears paid_out_by_user_id to preserve the historical "was paid" fact.

CREATE TABLE operation_payout_status (
    id                  UUID PRIMARY KEY,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation_id        UUID NOT NULL,
    participant_key     VARCHAR(255) NOT NULL,
    paid_out            BOOLEAN NOT NULL DEFAULT FALSE,
    paid_out_at         TIMESTAMP WITH TIME ZONE,
    paid_out_by_user_id UUID,
    CONSTRAINT fk_operation_payout_status_operation
        FOREIGN KEY (operation_id) REFERENCES operation (id) ON DELETE CASCADE,
    CONSTRAINT fk_operation_payout_status_paid_out_by_user
        FOREIGN KEY (paid_out_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_operation_payout_status_operation_participant
        UNIQUE (operation_id, participant_key)
);

CREATE INDEX idx_operation_payout_status_operation
    ON operation_payout_status (operation_id);

COMMENT ON TABLE operation_payout_status IS
    'Per-(operation, participant) flag toggled by mission managers to record whether a participant has been paid their operation payout. Absent row means "not paid yet".';
COMMENT ON COLUMN operation_payout_status.participant_key IS
    'Opaque participant key matching OperationPayoutDto.participantId: real user UUID stringified, or "guest_<name>".';
COMMENT ON COLUMN operation_payout_status.paid_out_at IS
    'Timestamp of the most recent transition to paid_out=TRUE. NULL while paid_out=FALSE; preserved as last-set value when toggling back is implemented later.';
COMMENT ON COLUMN operation_payout_status.paid_out_by_user_id IS
    'The mission-manager/admin/officer who most recently set paid_out=TRUE. NULL if the user was later deleted or the row has never been set to TRUE.';
