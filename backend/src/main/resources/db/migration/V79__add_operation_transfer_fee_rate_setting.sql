-- Seed the operation payout transfer-fee rate as a runtime-editable system setting so
-- officers/admins can adjust it from /admin/settings without a redeploy. The value mirrors
-- Star Citizen's in-game banking fee (Spectrum / mobiGlas charges 0.5% on every aUEC transfer
-- to the recipient) and is read by OperationService.getOperationPayouts() to net the fee out
-- of every per-participant payout amount. Stored as the decimal fraction (0.005 = 0.5%) so
-- the consumer can multiply directly without divide-by-100 ambiguity. Idempotent: a stale
-- developer DB that pre-existed this row keeps its current value, and any subsequent rerun
-- of this migration is a no-op via ON CONFLICT.
INSERT INTO system_setting (setting_key, setting_value, version)
VALUES ('operation.transfer_fee_rate', '0.005', 0)
ON CONFLICT (setting_key) DO NOTHING;
