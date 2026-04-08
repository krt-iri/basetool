package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.StarSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class StarSystemServiceTest {

    @Mock
    private StarSystemRepository starSystemRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private StarSystemService starSystemService;

    @Test
    void createStarSystem_DuplicateName_ShouldThrowException() {
        StarSystem starSystem = new StarSystem();
        starSystem.setName("Stanton");

        when(starSystemRepository.existsByNameIgnoreCase("Stanton")).thenReturn(true);

        assertThrows(DuplicateEntityException.class, () -> starSystemService.createStarSystem(starSystem));
    }

    @Test
    void deleteStarSystem_Success() {
        UUID systemId = UUID.randomUUID();
        StarSystem system = new StarSystem();
        system.setId(systemId);
        system.setName("Stanton");

        when(starSystemRepository.findById(systemId)).thenReturn(Optional.of(system));

        assertDoesNotThrow(() -> starSystemService.deleteStarSystem(systemId));
        verify(starSystemRepository, times(1)).delete(system);
    }
}
