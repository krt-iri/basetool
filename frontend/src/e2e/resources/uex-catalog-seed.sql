-- UEX-catalog snapshot for the E2E flows.
--
-- Ship types and refinery-hosting locations are normally UEX-synced and cannot
-- be created through the admin REST API on a fresh DB. This deterministic
-- snapshot is applied by BackendSeeder.seedCatalog() over JDBC right after the
-- stack is healthy, so the Hangar and Refinery flows have stable reference data
-- regardless of whether the (network-dependent) UEX sync has run.
--
-- Idempotent via fixed UUIDs + ON CONFLICT. Only the columns that are NOT NULL
-- without a DB default must be supplied (verified against the live schema);
-- `version` is left null (read paths tolerate it) and other columns default.

-- A city flagged has_refinery=true makes its linked location refinery-hosting
-- (LocationService.getRefineryLocations joins city/space_station on has_refinery).
INSERT INTO city (id, name, has_refinery)
VALUES ('11111111-1111-1111-1111-111111111111', 'E2E Refinery City', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO location (id, name, city_id, hidden)
VALUES ('22222222-2222-2222-2222-222222222222', 'E2E Refinery Hub',
        '11111111-1111-1111-1111-111111111111', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO manufacturer (id, name, abbreviation, hidden)
VALUES ('33333333-3333-3333-3333-333333333333', 'E2E Manufacturer', 'E2EM', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ship_type (id, name, manufacturer_id, hidden)
VALUES ('44444444-4444-4444-4444-444444444444', 'E2E Ship Type',
        '33333333-3333-3333-3333-333333333333', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO refining_method (id, name)
VALUES ('55555555-5555-5555-5555-555555555555', 'E2E Refining Method')
ON CONFLICT (id) DO NOTHING;
