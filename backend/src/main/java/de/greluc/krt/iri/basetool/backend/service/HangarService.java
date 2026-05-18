package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipDetailDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the personal hangar (ship inventory per user) plus the squadron-wide ship overview.
 *
 * <p>The owner check on update/delete is enforced here rather than via {@code @PreAuthorize}
 * because the rule is "must be the owner of the ship" — not expressible as a role check on the
 * authentication alone. Mission-unit references to a deleted ship are first nulled (the unit keeps
 * its name but loses the ship binding) before the ship itself is removed; an explicit {@code
 * entityManager.flush()} forces those updates to run before the delete so the FK constraint never
 * fires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HangarService {

  private final ShipRepository shipRepository;
  private final UserRepository userRepository;
  private final ShipTypeRepository shipTypeRepository;
  private final LocationRepository locationRepository;
  private final MissionUnitRepository missionUnitRepository;
  private final ShipMapper shipMapper;
  private final EntityManager entityManager;
  private final SquadronScopeService squadronScopeService;

  /**
   * Returns the paged ship list scoped to the caller's squadron context: admin without an active
   * squadron selection sees every ship; everyone else (including admins in switcher mode) sees only
   * ships of their effective squadron.
   *
   * @param pageable page request
   * @return paged list of ships in the caller's squadron context
   */
  public Page<Ship> getAllShips(@NotNull Pageable pageable) {
    UUID owningSquadronId = squadronScopeService.currentSquadronId().orElse(null);
    return shipRepository.findAllScoped(owningSquadronId, pageable);
  }

  /**
   * Adds a ship to a user's hangar. The ship's owning squadron is derived from the user's home
   * squadron at the time of the call - subsequent user-squadron moves do NOT cascade to existing
   * ships (a ship physically belongs to whichever squadron it was added in).
   *
   * @param userId owning user's id
   * @param dto ship payload (name, type, insurance, fitted, location)
   * @return the persisted ship
   * @throws NotFoundException when the user id does not resolve
   * @throws BadRequestException when the ship type or location id is missing/invalid
   */
  @Transactional
  public Ship addShip(@NotNull UUID userId, @NotNull ShipRequestDto dto) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    Ship ship = new Ship();
    ship.setName(dto.name());
    ship.setInsurance(dto.insurance());
    ship.setFitted(dto.fitted());
    ship.setOwner(user);
    ship.setOwningSquadron(user.getSquadron());
    ship.setShipType(
        shipTypeRepository
            .findById(dto.shipTypeId())
            .orElseThrow(() -> new BadRequestException("ShipType not found")));
    if (dto.locationId() != null) {
      ship.setLocation(
          locationRepository
              .findById(dto.locationId())
              .orElseThrow(() -> new BadRequestException("Location not found")));
    }
    return shipRepository.save(ship);
  }

  /**
   * Returns paged ship list owned by the user.
   *
   * @param userId owner id
   * @param pageable page request
   * @return paged ship list owned by the user
   */
  public Page<Ship> getMyShips(@NotNull UUID userId, @NotNull Pageable pageable) {
    return shipRepository.findByOwnerId(userId, pageable);
  }

  /**
   * Returns the per-ship-type squadron overview. When {@code includeOwnerDetails} is {@code true}
   * the returned DTOs carry the per-ship owner/location/fitted breakdown; when {@code false} only
   * the aggregated counts are exposed.
   *
   * <p>The role-based decision (only ADMIN/OFFICER see the owner breakdown) lives in {@code
   * HangarController} so this method stays pure business logic and the service layer keeps its
   * hands off {@link org.springframework.security.core.context.SecurityContextHolder} — see the
   * architecture rule enforced by {@code ArchitectureTest}.
   */
  public Page<SquadronShipOverviewDto> getSquadronOverview(
      Pageable pageable, boolean includeOwnerDetails) {
    UUID owningSquadronId = squadronScopeService.currentSquadronId().orElse(null);
    Page<Object[]> p = shipRepository.countShipsByType(owningSquadronId, pageable);

    List<de.greluc.krt.iri.basetool.backend.model.ShipType> types =
        includeOwnerDetails
            ? p.getContent().stream()
                .map(obj -> (de.greluc.krt.iri.basetool.backend.model.ShipType) obj[0])
                .toList()
            : java.util.Collections.emptyList();

    List<Ship> ships =
        includeOwnerDetails && !types.isEmpty()
            ? shipRepository.findByShipTypeIn(types)
            : java.util.Collections.emptyList();

    return p.map(
        obj -> {
          de.greluc.krt.iri.basetool.backend.model.ShipType type =
              (de.greluc.krt.iri.basetool.backend.model.ShipType) obj[0];
          List<SquadronShipDetailDto> details = null;
          if (includeOwnerDetails) {
            details =
                ships.stream()
                    .filter(s -> s.getShipType().getId().equals(type.getId()))
                    .map(
                        s ->
                            new SquadronShipDetailDto(
                                s.getOwner() != null ? s.getOwner().getEffectiveName() : "Unknown",
                                s.getLocation() != null ? s.getLocation().getName() : null,
                                s.isFitted()))
                    .toList();
          }

          return new SquadronShipOverviewDto(
              shipMapper.shipTypeToDto(type),
              ((Number) obj[1]).longValue(),
              obj[2] != null ? ((Number) obj[2]).longValue() : 0L,
              details);
        });
  }

  /**
   * Updates a ship owned by {@code userId}. Enforces "must be the owner" explicitly because the
   * rule is per-resource. Optimistic-lock check is explicit when {@code dto.version()} is non-null.
   *
   * @param userId calling user's id
   * @param shipId ship primary key
   * @param dto update payload
   * @return the persisted ship
   * @throws NotFoundException when the ship does not exist
   * @throws AccessDeniedException when the calling user is not the owner
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   * @throws BadRequestException when the ship type or location id is missing/invalid
   */
  @Transactional
  public Ship updateShip(@NotNull UUID userId, @NotNull UUID shipId, @NotNull ShipRequestDto dto) {
    Ship ship =
        shipRepository.findById(shipId).orElseThrow(() -> new NotFoundException("Ship not found"));

    if (dto.version() != null
        && ship.getVersion() != null
        && !ship.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Ship.class, shipId);
    }

    if (ship.getOwner() == null
        || ship.getOwner().getId() == null
        || !ship.getOwner().getId().equals(userId)) {
      throw new AccessDeniedException("Access denied: You do not own this ship");
    }

    ship.setName(dto.name());
    ship.setInsurance(dto.insurance());
    ship.setFitted(dto.fitted());

    ship.setShipType(
        shipTypeRepository
            .findById(dto.shipTypeId())
            .orElseThrow(() -> new BadRequestException("ShipType not found")));

    if (dto.locationId() != null) {
      ship.setLocation(
          locationRepository
              .findById(dto.locationId())
              .orElseThrow(() -> new BadRequestException("Location not found")));
    } else {
      ship.setLocation(null);
    }

    return shipRepository.save(ship);
  }

  /**
   * Deletes a ship after detaching any mission-unit references. Owner check identical to {@link
   * #updateShip}. The explicit {@code flush()} guarantees the unit detach SQL runs before the ship
   * DELETE so the FK constraint never fires.
   *
   * @param userId calling user's id
   * @param shipId ship primary key
   * @throws NotFoundException when the ship does not exist
   * @throws AccessDeniedException when the calling user is not the owner
   */
  @Transactional
  public void deleteShip(@NotNull UUID userId, @NotNull UUID shipId) {
    Ship ship =
        shipRepository.findById(shipId).orElseThrow(() -> new NotFoundException("Ship not found"));

    if (ship.getOwner() == null
        || ship.getOwner().getId() == null
        || !ship.getOwner().getId().equals(userId)) {
      throw new AccessDeniedException("Access denied: You do not own this ship");
    }

    missionUnitRepository
        .findByShipId(shipId)
        .forEach(
            unit -> {
              unit.setShip(null);
              missionUnitRepository.save(unit);
            });

    entityManager.flush();
    shipRepository.delete(ship);
  }

  /**
   * Removes every ship owned by a user — used when an admin deletes the user account so no orphan
   * ship rows remain. Same unit-detach-then-delete sequence as {@link #deleteShip}.
   *
   * @param userId owning user's id
   */
  @Transactional
  public void deleteAllShipsForUser(@NotNull UUID userId) {
    List<Ship> ships = shipRepository.findByOwnerId(userId);
    if (ships.isEmpty()) {
      log.debug("deleteAllShipsForUser: no ships found for user {}", userId);
      return;
    }
    log.info("deleteAllShipsForUser: unlinking {} ships for user {}", ships.size(), userId);
    for (Ship ship : ships) {
      missionUnitRepository
          .findByShipId(ship.getId())
          .forEach(
              unit -> {
                unit.setShip(null);
                missionUnitRepository.save(unit);
              });
    }
    entityManager.flush();
    shipRepository.deleteAll(ships);
    log.info("deleteAllShipsForUser: deleted {} ships for user {}", ships.size(), userId);
  }

  /**
   * Squadron-wide bulk reset of the {@code fitted} flag on every ship. Used by admins after a major
   * event (patch wipe etc.) so members re-fit their ships instead of carrying stale state.
   */
  @Transactional
  public void resetAllFittedStatus() {
    shipRepository.resetAllFitted();
  }
}
