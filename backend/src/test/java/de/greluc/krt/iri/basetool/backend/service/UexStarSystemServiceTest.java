package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexStarSystemDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.repository.StarSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UexStarSystemServiceTest {

    @Mock
    private UexClient uexClient;

    @Mock
    private StarSystemRepository starSystemRepository;

    @InjectMocks
    private UexStarSystemService uexStarSystemService;

    @Test
    void shouldProcessStarSystemDtoAndCreateNewStarSystem() {
        // Given
        UexStarSystemDto dto = UexStarSystemDto.builder()
                .id(1)
                .name("Stanton")
                .code("ST")
                .isAvailableLive(1)
                .wiki("https://starcitizen.tools/Stanton")
                .jurisdictionName("UEE")
                .factionName("UEE")
                .build();

        when(uexClient.getStarSystems()).thenReturn(List.of(dto));
        when(starSystemRepository.findByIdSystem(1)).thenReturn(Optional.empty());
        when(starSystemRepository.findByName("Stanton")).thenReturn(Optional.empty());
        
        StarSystem savedSystem = new StarSystem();
        savedSystem.setId(UUID.randomUUID());
        savedSystem.setIdSystem(1);
        savedSystem.setName("Stanton");
        
        // Mock save for both the initial creation and the subsequent update
        when(starSystemRepository.save(any(StarSystem.class))).thenAnswer(i -> i.getArgument(0));

        // When
        uexStarSystemService.fetchAndProcessStarSystems();

        // Then
        ArgumentCaptor<StarSystem> systemCaptor = ArgumentCaptor.forClass(StarSystem.class);
        verify(starSystemRepository, atLeastOnce()).save(systemCaptor.capture());
        
        StarSystem captured = systemCaptor.getValue();
        assertEquals(1, captured.getIdSystem());
        assertEquals("Stanton", captured.getName());
        assertEquals(true, captured.getIsAvailableLive());
        assertEquals("https://starcitizen.tools/Stanton", captured.getWiki());
        assertEquals("UEE", captured.getJurisdictionName());
        assertEquals("UEE", captured.getFactionName());
    }
}