package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.service.HangarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class OptimisticLockingTest {

    @Autowired
    private HangarService hangarService;

    @Autowired
    private ShipRepository shipRepository;

    @Test
    @Transactional
    public void testVersionUpdate() {
        Ship ship = shipRepository.findAll().stream().findFirst().orElse(null);
        if (ship != null) {
            UUID shipId = ship.getId();
            UUID userId = ship.getOwner().getId();
            Long version = ship.getVersion();

            ShipRequestDto dto = new ShipRequestDto(
                "New Name",
                ship.getShipType().getId(),
                "LTI",
                ship.getLocation() != null ? ship.getLocation().getId() : null,
                true,
                version
            );
            
            Ship updated = hangarService.updateShip(userId, shipId, dto);
            assertNotNull(updated);
        }
    }
}
