-- Phase 1 of the Job-Order rework (#341): OrgUnit prerequisites.
--
-- 1) Per-org-unit "profit eligible" flag deciding whether an org unit (squadron OR Spezialkommando)
--    may be picked as the responsible (processing) org unit of a Job Order. Applies to BOTH kinds:
--    squadrons and SKs belong to different Kartell departments and only Profit-side units process
--    orders. An SK of another department may still PLACE orders (as the requesting org unit) but must
--    not appear in the responsible picker. Defaults to FALSE for every row; an admin opts each
--    squadron / SK in through the dedicated PATCH /api/v1/squadrons/{id}/profit-eligible /
--    PATCH /api/v1/special-commands/{id}/profit-eligible endpoints. No CHECK constraint is needed
--    (unlike is_promotion_enabled): eligibility is not a security invariant, just a picker filter.
ALTER TABLE org_unit
    ADD COLUMN is_profit_eligible BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Designated "intake" Spezialkommando for anonymous/guest Job-Order creation. Guest creations are
--    forced onto this SK (public) instead of letting an unauthenticated caller pick a squadron.
--    Seeded empty: an admin selects the intake SK on the /admin/settings page. The value, when set,
--    is the UUID of a kind = 'SPECIAL_COMMAND' org_unit row (validated in the application layer).
INSERT INTO system_setting (setting_key, setting_value, version)
VALUES ('job_order.intake_special_command_id', '', 0);
