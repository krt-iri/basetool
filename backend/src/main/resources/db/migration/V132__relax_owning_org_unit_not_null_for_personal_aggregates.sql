-- =============================================================================
-- Ownerless personal aggregates (#355 follow-up) — DROP NOT NULL on
-- `owning_org_unit_id` for the three owner-carrying personal aggregates.
--
-- Context: V102 tightened `owning_org_unit_id` to NOT NULL on every staffel-
-- scoped aggregate. That made `OwnerScopeService.resolveOrgUnitForPickerOutput`
-- (which rejects a user with zero org-unit memberships) a hard create-time
-- gate. But a user without any Staffel/SK membership is a LEGITIMATE state, and
-- such a user must still be able to add ships to their hangar, raise refinery
-- orders, and record personal inventory. Those three aggregates each carry
-- their own per-user owner column (`ship.owner_id`, `refinery_order.owner_id`,
-- `inventory_item.user_id`), so a row with a NULL `owning_org_unit_id` is fully
-- attributable to its owning user — an "ownerless personal aggregate". The
-- application now stamps NULL (instead of 400ing) when a membershipless user
-- creates one of these, and scopes such rows to their owning user only
-- (`OwnerScopeService.canSee*/canEdit*` owner-only branch).
--
-- mission / operation / job_order are deliberately NOT relaxed: they have no
-- per-user owner column to fall back on (they are org-owned by construction),
-- so their `owning_org_unit_id` stays NOT NULL.
--
-- Non-destructive: DROP NOT NULL only relaxes a constraint (catalog-only, no
-- table rewrite, no data loss) and is a no-op if the column is already
-- nullable. Existing rows keep their populated owner. Rollback would re-tighten
-- via `ALTER COLUMN owning_org_unit_id SET NOT NULL`, which fails only if an
-- ownerless row has since been created — backfill those from the owner's
-- membership (or delete them) before re-tightening.
-- =============================================================================

ALTER TABLE ship            ALTER COLUMN owning_org_unit_id DROP NOT NULL;
ALTER TABLE refinery_order  ALTER COLUMN owning_org_unit_id DROP NOT NULL;
ALTER TABLE inventory_item  ALTER COLUMN owning_org_unit_id DROP NOT NULL;
