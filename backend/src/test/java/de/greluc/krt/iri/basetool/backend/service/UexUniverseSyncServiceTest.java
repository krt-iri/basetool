package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCityDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UexUniverseSyncServiceTest {

    @Mock
    private UexClient uexClient;

    @Mock
    private CityRepository cityRepository;
    @Mock private FactionRepository factionRepository;
    @Mock private JurisdictionRepository jurisdictionRepository;
    @Mock private MoonRepository moonRepository;
    @Mock private OrbitRepository orbitRepository;
    @Mock private OutpostRepository outpostRepository;
    @Mock private PlanetRepository planetRepository;
    @Mock private PoiRepository poiRepository;
    @Mock private SpaceStationRepository spaceStationRepository;
    @Mock private TerminalRepository terminalRepository;

    @Mock private LocationRepository locationRepository;

    @InjectMocks
    private UexUniverseSyncService service;

    @Test
    void shouldSyncCitiesSuccessfully() {
        // Given
        UexCityDto cityDto = UexCityDto.builder()
                .id(1)
                .name("Lorville")
                .isAvailableLive(1)
                .build();
        
        when(uexClient.getCities()).thenReturn(List.of(cityDto));
        when(cityRepository.findByIdCity(1)).thenReturn(Optional.empty());
        when(cityRepository.findByName("Lorville")).thenReturn(Optional.empty());
        when(cityRepository.save(any(City.class))).thenAnswer(invocation -> {
            City c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(java.util.UUID.randomUUID());
            return c;
        });

        de.greluc.krt.iri.basetool.backend.model.Location mockLocation = new de.greluc.krt.iri.basetool.backend.model.Location();
        when(locationRepository.findByCityId(any())).thenReturn(Optional.of(mockLocation));
        when(locationRepository.save(any())).thenReturn(mockLocation);

        // When
        service.syncCities();

        // Then
        verify(uexClient, times(1)).getCities();
        verify(cityRepository, times(2)).save(any(City.class)); // 1 for save new entity, 1 for update entity
    }

    @Test
    void shouldSkipSyncIfNoData() {
        // Given
        when(uexClient.getCities()).thenReturn(List.of());

        // When
        service.syncCities();

        // Then
        verify(uexClient, times(1)).getCities();
        verify(cityRepository, never()).save(any());
    }
}
