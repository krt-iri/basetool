# Changelog

## [Unreleased]

### Added
- `MissionDto`, `MissionParticipantDto`, `MissionUnitDto`, `MissionCrewDto`, `MissionFinanceEntryDto`, `MissionLeadTypeDto` DTO records for the Mission API.
- Record-based request DTOs: `AddUnitRequest`, `AddParticipantRequest`, `AddParticipantPublicRequest`, `AddCrewRequest`, `UpdateCrewRequest`, `UpdateParticipantRequest`, `AddFinanceEntryRequest`.
- `MissionMapper` (MapStruct, Spring component) for Mission entity-to-DTO conversion.
- Server-side pagination and sorting for mission list and search endpoints using `Pageable` and `PageResponse`.
- OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Tag`) on `MissionController` and `SystemController`.
- `PingResponse.timestamp` now uses `Instant` (UTC) instead of `String`.

### Fixed
- Fixed access control for Officers and Admins in Refinery and Job Order management by utilizing `RoleHierarchy` instead of manual role string checks.
- Restored missing action buttons and enabled owner dropdowns for users with inherited Logistical permissions.
- Corrected role inheritance in both Frontend and Backend controllers to ensure consistent behavior across the application.
- Refactored `MissionCrew` to link to `MissionParticipant` instead of `User`. This ensures that crew members must be registered participants of the mission.
- Updated API response for Mission Crew to include full participant details.
- Added `MaterialType` to `Material` (RAW, REFINED, NO_REFINE).
- Renamed `RefinedGood` to `RefineryGood`.
- Enforced that `RefineryGood` must refer to a `Material` of type `RAW`.
- `MissionController` now returns `MissionDto` / `PageResponse<MissionDto>` instead of leaking JPA entities.
- Reverted `/api/v1/system/ping` to return `Map<String, String>` for backward compatibility until sunset (2026-12-31); `/api/v2/system/ping` returns `PingResponse` with `Instant` timestamp.
- `ManufacturerDto` field aligned with entity (`abbreviation` instead of `code`), resolving ShipMapper warning.
- Migrated remaining hardcoded dependency versions (`springdoc-openapi`, `lombok-mapstruct-binding`, `okhttp3-mockwebserver`) to `refreshVersions` placeholders.
- Resolved MapStruct unmapped property warnings (`code` in ShipMapper, `parentId` in MissionMapper).

## [v1.0.0](https://github.com/krt-iri/hq-backend/releases/tag/v1.0.0)

- Initial release.