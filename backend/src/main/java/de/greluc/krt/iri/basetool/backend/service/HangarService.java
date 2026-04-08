package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionUnitRepository;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipDetailDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.backend.mapper.ShipMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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

    public Page<Ship> getAllShips(@NotNull Pageable pageable) {
        return shipRepository.findAll(pageable);
    }

    @Transactional
    public Ship addShip(@NotNull UUID userId, @NotNull ShipRequestDto dto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Ship ship = new Ship();
        ship.setName(dto.name());
        ship.setInsurance(dto.insurance());
        ship.setFitted(dto.fitted());
        ship.setOwner(user);
        ship.setShipType(shipTypeRepository.findById(dto.shipTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ShipType not found")));
        if (dto.locationId() != null) {
            ship.setLocation(locationRepository.findById(dto.locationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location not found")));
        }
        return shipRepository.save(ship);
    }

    public Page<Ship> getMyShips(@NotNull UUID userId, @NotNull Pageable pageable) {
        return shipRepository.findByOwnerId(userId, pageable);
    }

    public Page<SquadronShipOverviewDto> getSquadronOverview(Pageable pageable) {
        Page<Object[]> p = shipRepository.countShipsByType(pageable);

        boolean includeDetails = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            includeDetails = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OFFICER"));
        }

        final boolean fetchDetails = includeDetails;
        List<de.greluc.krt.iri.basetool.backend.model.ShipType> types = fetchDetails 
                ? p.getContent().stream().map(obj -> (de.greluc.krt.iri.basetool.backend.model.ShipType) obj[0]).toList()
                : java.util.Collections.emptyList();
                
        List<Ship> ships = fetchDetails && !types.isEmpty() ? shipRepository.findByShipTypeIn(types) : java.util.Collections.emptyList();

        return p.map(obj -> {
            de.greluc.krt.iri.basetool.backend.model.ShipType type = (de.greluc.krt.iri.basetool.backend.model.ShipType) obj[0];
            List<SquadronShipDetailDto> details = null;
            if (fetchDetails) {
                details = ships.stream()
                        .filter(s -> s.getShipType().getId().equals(type.getId()))
                        .map(s -> new SquadronShipDetailDto(
                                s.getOwner() != null ? s.getOwner().getEffectiveName() : "Unknown",
                                s.getLocation() != null ? s.getLocation().getName() : null,
                                s.isFitted()
                        ))
                        .toList();
            }

            return new SquadronShipOverviewDto(
                    shipMapper.shipTypeToDto(type),
                    ((Number) obj[1]).longValue(),
                    obj[2] != null ? ((Number) obj[2]).longValue() : 0L,
                    details
            );
        });
    }

    @Transactional
    public Ship updateShip(@NotNull UUID userId, @NotNull UUID shipId, @NotNull ShipRequestDto dto) {
        Ship ship = shipRepository.findById(shipId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ship not found"));

        if (dto.version() != null && ship.getVersion() != null && !ship.getVersion().equals(dto.version())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Ship.class, shipId);
        }

        if (ship.getOwner() == null || ship.getOwner().getId() == null || !ship.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this ship");
        }

        ship.setName(dto.name());
        ship.setInsurance(dto.insurance());
        ship.setFitted(dto.fitted());
        
        ship.setShipType(shipTypeRepository.findById(dto.shipTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ShipType not found")));

        if (dto.locationId() != null) {
            ship.setLocation(locationRepository.findById(dto.locationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location not found")));
        } else {
            ship.setLocation(null);
        }

        return shipRepository.save(ship);
    }

    @Transactional
    public void deleteShip(@NotNull UUID userId, @NotNull UUID shipId) {
        Ship ship = shipRepository.findById(shipId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ship not found"));

        if (ship.getOwner() == null || ship.getOwner().getId() == null || !ship.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this ship");
        }

        missionUnitRepository.findByShipId(shipId).forEach(unit -> {
            unit.setShip(null);
            missionUnitRepository.save(unit);
        });

        entityManager.flush();
        shipRepository.delete(ship);
    }

    @Transactional
    public void resetAllFittedStatus() {
        shipRepository.resetAllFitted();
    }
}
