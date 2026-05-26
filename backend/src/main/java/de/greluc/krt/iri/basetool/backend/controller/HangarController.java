package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewImportResponseDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.backend.service.HangarImportService;
import de.greluc.krt.iri.basetool.backend.service.HangarService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST surface for the personal hangar (own ships), the squadron-wide overview, the admin per-user
 * hangar, and the third-party ship-export JSON import (CCU Game Fleetview / HangarXPLOR Shiplist).
 *
 * <p>{@code /my-ships} reads the calling user's JWT to derive the owner id — never accepts it from
 * the URL — so a caller cannot view another user's hangar via this endpoint. The admin-only {@code
 * /users/{userId}/ships} surface takes the user id from the path explicitly and is gated by {@code
 * hasRole('ADMIN')}. The {@code /squadron-overview} endpoint shapes its response based on the
 * caller's role: only ADMIN/OFFICER see the per-ship owner details, every other authenticated
 * caller gets just the aggregated counts.
 */
@RestController
@RequestMapping("/api/v1/hangar")
@RequiredArgsConstructor
public class HangarController {
  private final HangarService hangarService;
  private final HangarImportService hangarImportService;
  private final UserService userService;
  private final ShipMapper shipMapper;

  /**
   * Lists the calling user's own ships.
   *
   * @return paged ship DTOs
   */
  @GetMapping("/my-ships")
  @Transactional(readOnly = true)
  public PageResponse<ShipDto> getMyShips(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
    Page<Ship> p = hangarService.getMyShips(userService.getUserIdFromJwt(jwt), pageable);
    List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
    return new PageResponse<>(
        dtos,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lists ships across all users. Requires the {@code HANGAR_READ} authority.
   *
   * @return paged ship DTOs
   */
  @GetMapping("/ships")
  @PreAuthorize("hasAuthority('HANGAR_READ')")
  @Transactional(readOnly = true)
  public PageResponse<ShipDto> getAllShips(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
    Page<Ship> p = hangarService.getAllShips(pageable);
    List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
    return new PageResponse<>(
        dtos,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Per-ship-type aggregated count across the squadron. Admins and officers additionally see the
   * per-ship owner / location / fitted breakdown; everyone else sees only the totals — the
   * role-driven shaping happens at the HTTP boundary so the service stays free of {@code
   * SecurityContextHolder} reads (the ArchUnit rule).
   *
   * @return paged overview DTOs
   */
  @GetMapping("/squadron-overview")
  @Transactional(readOnly = true)
  public PageResponse<SquadronShipOverviewDto> getSquadronOverview(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      Authentication authentication) {
    // Role-based shaping of the response is decided HERE, at the HTTP boundary, so
    // the service stays pure business logic and does not need to read
    // SecurityContextHolder itself (architecture rule enforced by ArchitectureTest).
    boolean includeOwnerDetails =
        authentication != null
            && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_OFFICER".equals(role));
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("shipType.name"), "shipType.name");
    Page<SquadronShipOverviewDto> p =
        hangarService.getSquadronOverview(pageable, includeOwnerDetails);
    return new PageResponse<>(
        p.getContent(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Adds a ship to the calling user's hangar.
   *
   * @return the persisted ship DTO
   */
  @PostMapping("/ships")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public ShipDto addShip(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ShipRequestDto shipRequest) {
    return shipMapper.toDto(hangarService.addShip(userService.getUserIdFromJwt(jwt), shipRequest));
  }

  /**
   * Updates one of the calling user's ships. Service-layer ownership check ensures cross-user
   * access is rejected.
   *
   * @return the persisted ship DTO
   */
  @PutMapping("/ships/{id}")
  @PreAuthorize("isAuthenticated() and @ownerScopeService.canEditShip(#id)")
  @Transactional
  public ShipDto updateMyShip(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid @NotNull ShipRequestDto shipRequest) {
    return shipMapper.toDto(
        hangarService.updateShip(userService.getUserIdFromJwt(jwt), id, shipRequest));
  }

  /**
   * Deletes one of the calling user's ships. Mission-unit references are detached before delete.
   */
  @DeleteMapping("/ships/{id}")
  @PreAuthorize("isAuthenticated() and @ownerScopeService.canEditShip(#id)")
  public void deleteMyShip(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID id) {
    hangarService.deleteShip(userService.getUserIdFromJwt(jwt), id);
  }

  /**
   * Removes every ship the calling user owns. Mission-unit references to those ships are detached
   * before delete so no FK constraint fires.
   *
   * @return 204 No Content
   */
  @Operation(
      summary = "Alle eigenen Schiffe löschen",
      description =
          "Löscht alle Schiffe des authentifizierten Nutzers. Verknüpfungen mit"
              + " Mission-Einheiten werden sicher aufgelöst.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Alle Schiffe erfolgreich gelöscht"),
    @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
    @ApiResponse(responseCode = "403", description = "Keine Berechtigung")
  })
  @DeleteMapping("/ships")
  @PreAuthorize("hasAuthority('HANGAR_WRITE')")
  public ResponseEntity<Void> deleteAllMyShips(@AuthenticationPrincipal Jwt jwt) {
    hangarService.deleteAllShipsForUser(userService.getUserIdFromJwt(jwt));
    return ResponseEntity.noContent().build();
  }

  // Admin endpoints

  /**
   * Admin-only: lists a target user's hangar. User id comes from the path (not the JWT) so admins
   * can inspect any user's fleet.
   *
   * @return paged ship DTOs
   */
  @GetMapping("/users/{userId}/ships")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public PageResponse<ShipDto> getUserShips(
      @PathVariable @NotNull UUID userId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "insurance", "fitted", "id"), "name");
    Page<Ship> p = hangarService.getMyShips(userId, pageable);
    List<ShipDto> dtos = p.getContent().stream().map(shipMapper::toDto).toList();
    return new PageResponse<>(
        dtos,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /** Admin-only: adds a ship to a target user's hangar. */
  @PostMapping("/users/{userId}/ships")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ShipDto addUserShip(
      @PathVariable @NotNull UUID userId, @RequestBody @Valid ShipRequestDto shipRequest) {
    return shipMapper.toDto(hangarService.addShip(userId, shipRequest));
  }

  /** Admin-only: updates a target user's ship. */
  @PutMapping("/users/{userId}/ships/{shipId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ShipDto updateUserShip(
      @PathVariable @NotNull UUID userId,
      @PathVariable @NotNull UUID shipId,
      @RequestBody @Valid @NotNull ShipRequestDto shipRequest) {
    return shipMapper.toDto(hangarService.updateShip(userId, shipId, shipRequest));
  }

  /** Admin-only: deletes a target user's ship. */
  @DeleteMapping("/users/{userId}/ships/{shipId}")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteUserShip(
      @PathVariable @NotNull UUID userId, @PathVariable @NotNull UUID shipId) {
    hangarService.deleteShip(userId, shipId);
  }

  /**
   * Imports a ship-export JSON file (CCU Game Fleetview or HangarXPLOR Shiplist — the format is
   * auto-detected from the first array element). Parses the file via {@code HangarImportService}
   * and creates only the missing rows so existing hangar contents are never lost or duplicated. The
   * caller's JWT is the owner of the new rows.
   *
   * @param jwt caller's JWT — its {@code sub} claim becomes the new rows' owner id
   * @param file uploaded JSON file
   * @return import summary (created / skipped / duplicate counts plus the unmatched-ship list)
   */
  @PostMapping("/import/ships")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public FleetviewImportResponseDto importShips(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") @NotNull MultipartFile file) {
    return hangarImportService.importShips(userService.getUserIdFromJwt(jwt), file);
  }

  /**
   * Legacy path for the ship-import endpoint, kept for one year so existing automation does not
   * break. Delegates to the same service as {@link #importShips(Jwt, MultipartFile)}; the response
   * is identical. New clients should target {@code /api/v1/hangar/import/ships} which is
   * format-neutral (the original {@code /import/fleetview} name predates HangarXPLOR support).
   *
   * @param jwt caller's JWT — its {@code sub} claim becomes the new rows' owner id
   * @param file uploaded JSON file
   * @return import summary (created / skipped / duplicate counts plus the unmatched-ship list)
   * @deprecated use {@link #importShips(Jwt, MultipartFile)} via {@code
   *     /api/v1/hangar/import/ships} instead — the {@code Sunset} and {@code Link} response headers
   *     carry the same hint.
   */
  @PostMapping("/import/fleetview")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  @ApiDeprecation(sunset = "2027-05-14", replacement = "/api/v1/hangar/import/ships")
  @Deprecated(since = "2026-05-14", forRemoval = true)
  public FleetviewImportResponseDto importFleetview(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") @NotNull MultipartFile file) {
    return hangarImportService.importShips(userService.getUserIdFromJwt(jwt), file);
  }

  /** Bulk reset of the {@code fitted} flag on every ship in the squadron. ADMIN/OFFICER-only. */
  @PostMapping("/ships/reset-fitted")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public void resetAllFittedStatus() {
    hangarService.resetAllFittedStatus();
  }
}
