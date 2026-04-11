# Changelog

## [Unreleased]
### Changed
- **Frontend**: Moved the "Impressum" link from the "Rechtliches" dropdown to the top level of the sidebar, directly after the "Rechtliches" menu.

### Added
- **Backend**: Added database tables `job_order_handover` and `job_order_handover_item` for tracking material handovers.
- **Backend**: Implemented `JobOrderHandoverService` to process handovers and correctly reduce `InventoryItem` amounts using pessimistic locking.
- **Backend**: Added POST endpoint `/api/v1/orders/{id}/handovers` in `JobOrderController` to handle the handover creation.
- **Frontend**: Added DTOs and API client methods for handover creation.
- **Frontend**: Implemented handover modal in `orders-detail.html` with dynamic selection of available inventory items.
- **i18n**: Added translation keys for the new handover UI (`orders.detail.handover`, `orders.handover.*` etc.).

### Added
- **Frontend**: Die auf-/zugeklappten Zeilen in den Lagerübersichten (`inventory-my`, `inventory-admin`) werden nun benutzerspezifisch (per LocalStorage) gespeichert und beim Neuladen der Seite wiederhergestellt.
- **Frontend**: Added a modal to edit existing job orders (squadron, handle, materials) directly from the details view for authorized users (Logisticians, Officers, Admins).
- **Frontend**: Job order status is now an asynchronous dropdown menu that updates immediately without requiring a full form submission or a separate "Update" button.
- **Backend**: Implemented `unlinkJobOrderMaterial` in `InventoryItemRepository` and `JobOrderService` to properly decouple inventory items when a specific material is removed from an active job order during an update.
- **Backend**: Modified `JobOrderController` and `JobOrderPageController` to process job order details updates via DTOs.
- **Frontend**: Added dynamic unit labels (e.g. "SCU" or "Stück") behind all quantity numbers across the inventory views (`inventory-admin`, `inventory-my`, `inventory-index`, `inventory-material`) and mission details, formatting decimals based on the material's quantity type.
- **Backend & Frontend**: Added distinct handling for quantity types (`SCU` and `Stück/PIECE`) across inventory and refinery order storage dialogs.
- **Backend**: Implemented `@ValidQuantityAmount` Jakarta Constraint for `InventoryItemCreateDto`, `InventoryItemUpdateDto`, and `RefineryOrderStoreItemDto` to ensure PIECE quantities are integers and SCU quantities have max 3 decimal places.
- **Database**: Added Flyway migration script `V56__update_inventory_quantities.sql` to floor legacy `PIECE` amounts to whole integers.
- **Frontend**: `RefineryOrder` storage dialog now intelligently prefills amounts: SCU quantities are calculated and rounded to 3 decimals based on system settings, while PIECE quantities receive the exact integer units.
- **Frontend**: Added dynamic HTML5 constraints (`step="1"` vs `step="0.001"`) and visual quantity type indicators for `SCU` / `Stück` to all inventory input and book-out modals.

### Fixed
- **Frontend**: Fixed display of refinery order quantities in mission details (`mission-detail.html`). The amount is now dynamically scaled (divided by 100 for SCU) and the unit type (SCU/Stück) is displayed per row instead of being hardcoded in the column header.
- **Frontend**: Changed the redirect destination after successfully storing a refinery order. The user is now redirected back to the refinery management page (`/refinery-orders`) instead of the inventory page (`/inventory`), improving the user workflow.
- **Frontend/Backend**: Fixed an issue where the Job Orders page (`orders-index.html`) was loaded twice due to a JavaScript redirect for the filter parameters. The filter state (`orders_filter_status`) is now managed completely server-side via a cookie in `JobOrderPageController.java`.
- **Frontend**: Fixed "unsaved changes" warning that incorrectly appeared when leaving the page immediately after successfully updating the asynchronous status dropdown in the `orders-detail.html` view.
- **Frontend**: Fixed an issue in `orders-detail.html` where users with edit rights for a `JobOrder` would still see the static status label badge in addition to the interactive dropdown. The static label is now hidden for users with the `LOGISTICIAN` role (who are permitted to change the status).
- **Frontend**: Fixed `minQuality` filter dropdown in `inventory-admin.html` to include `1000` as a valid option (range from 100 to 1000) to match the material quality specification.
- **Frontend**: Fixed HTML max validation limit for "Min. Qualität" (Minimum Quality) in `orders-create.html` and `orders-detail.html`. Increased the maximum allowed input from 100/999 to 1000 to match the backend specification for materials.
- **Frontend**: Fixed `orders-detail.html` edit modal background to use the standard `.modal` class instead of a transparent `.modal-backdrop` for better readability.
- **Frontend**: Fixed label styles (Staffel, Handle, Material, Min. Qualität, Menge) in the `orders-detail.html` edit modal to match the KRT design (Lato, bold, primary color).
- **Frontend**: Fixed the "Staffel" dropdown in the `orders-detail.html` edit modal to display only the squadron name instead of the full `SquadronDto` object string.
- **i18n**: Added missing translations for the "Remove" (Entfernen) material button in `messages.properties` (DE/EN).
- **Frontend**: Fixed 500 server error when viewing inventory lists (global, my, material details, mission details) caused by an invalid SpEL expression combining variables and `#messages.msg` implicitly. Refactored templates to use Thymeleaf literal substitutions `|...|` for safe string concatenation.
- **Frontend**: Fixed 500 server error when opening a refinery order caused by an invalid SpEL expression containing Thymeleaf message variables in the storage dialog.
- **Frontend**: Fixed label styling for `(Stück)` and `(SCU)` quantity markers in inventory input and refinery order details to inherit the standard orange and bold label styling.
- **Testing**: Fixed legacy `MissionAccessControlTest` and `MissionGuestAccessTest` which failed due to missing required constraints on DTO updates.
- Added Auto-Save feature on the Materials admin page. Changes in the material dropdown menus (Quantity Type, Category, Refined Material) are now saved asynchronously. The manual "Save" buttons have been removed.
- `MaterialUpdateAjaxRequest` DTO and an AJAX endpoint in `AdminMaterialsPageController` to process asynchronous material updates.
- `MissionDto`, `MissionParticipantDto`, `MissionUnitDto`, `MissionCrewDto`, `MissionFinanceEntryDto`, `MissionLeadTypeDto` DTO records for the Mission API.
- Record-based request DTOs: `AddUnitRequest`, `AddParticipantRequest`, `AddParticipantPublicRequest`, `AddCrewRequest`, `UpdateCrewRequest`, `UpdateParticipantRequest`, `AddFinanceEntryRequest`.
- `MissionMapper` (MapStruct, Spring component) for Mission entity-to-DTO conversion.
- Server-side pagination and sorting for mission list and search endpoints using `Pageable` and `PageResponse`.
- OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Tag`) on `MissionController` and `SystemController`.
- `PingResponse.timestamp` now uses `Instant` (UTC) instead of `String`.

### Fixed
- Fixed an issue where submitting the refinery order store form multiple times or overriding the pre-filled yield amount led to duplicated inventory material. Orders already marked as `COMPLETED` can no longer be stored again, and the frontend amount field is now disabled (readonly) if the output quantity is fixed by the order.
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