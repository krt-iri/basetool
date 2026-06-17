-- =====================================================================
-- V158 - manufacturer.abbreviation is a non-unique display label
-- =====================================================================
-- Why: the scheduled UEX /companies sync (UexManufacturerService) derives
-- each manufacturer's `abbreviation` from the company's `nickname`, falling
-- back to `name`. UEX legitimately ships multiple *distinct* companies that
-- share a nickname. Observed in prod (v0.5.4): two Esperia-derived companies
-- (e.g. id 278 "Esperia Incorporation" plus a second Esperia row) both reduce
-- to abbreviation "Esperia". Each resolves to its own row by `uex_company_id`,
-- so the match chain cannot merge them — whichever the sync UPDATEs second hits
-- the V1 UNIQUE constraint `manufacturer_abbreviation_key`
-- ("duplicate key ... (abbreviation)=(Esperia)"). Because the whole sweep ran
-- as one transaction, that single violation marked it rollback-only and every
-- manufacturer update for the run (and the rest of the UEX sweep behind it) was
-- discarded. See REQ-DATA-004.
--
-- `abbreviation` is a display label, not an identity key. The identity keys
-- (`uex_company_id`, `scwiki_uuid`) and the human-canonical `name` keep their
-- UNIQUE constraints; only the abbreviation uniqueness is wrong and is dropped
-- here, letting two manufacturers share a short code without poisoning the sync.
--
-- Up-only (no undo script): re-adding the constraint is only possible once any
-- duplicate abbreviations have been manually reconciled, so a corrective forward
-- migration would do that reconciliation first.

ALTER TABLE manufacturer
    DROP CONSTRAINT IF EXISTS manufacturer_abbreviation_key;
