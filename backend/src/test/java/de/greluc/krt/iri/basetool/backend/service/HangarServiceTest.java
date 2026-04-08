package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionUnitRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HangarServiceTest {

    @Mock
    private ShipRepository shipRepository;
    @Mock
    private ShipTypeRepository shipTypeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private MissionUnitRepository missionUnitRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private HangarService hangarService;

    @Test
    void testUpdateShipOptimisticLocking_ThrowsWhenVersionsDiffer() {
        UUID userId = UUID.randomUUID();
        UUID shipId = UUID.randomUUID();
        
        User owner = new User();
        owner.setId(userId);
        
        Ship ship = new Ship();
        ship.setId(shipId);
        ship.setVersion(1L);
        ship.setOwner(owner);

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));

        ShipRequestDto request = new ShipRequestDto(
            "Test", UUID.randomUUID(), "LTI", null, false, 0L // different version
        );

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> 
            hangarService.updateShip(userId, shipId, request)
        );
    }
    
    @Test
    void testUpdateShipOptimisticLocking_SucceedsWhenVersionsMatch() {
        UUID userId = UUID.randomUUID();
        UUID shipId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        
        User owner = new User();
        owner.setId(userId);
        
        Ship ship = new Ship();
        ship.setId(shipId);
        ship.setVersion(1L);
        ship.setOwner(owner);
        
        ShipType type = new ShipType();
        type.setId(typeId);

        when(shipRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(shipTypeRepository.findById(typeId)).thenReturn(Optional.of(type));
        when(shipRepository.save(any(Ship.class))).thenReturn(ship);

        ShipRequestDto request = new ShipRequestDto(
            "Test", typeId, "LTI", null, false, 1L // matching version
        );

        hangarService.updateShip(userId, shipId, request);
        
        verify(shipRepository, times(1)).save(ship);
    }
}
