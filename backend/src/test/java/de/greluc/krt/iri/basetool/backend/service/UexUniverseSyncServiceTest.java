package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexFactionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexJurisdictionDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexMoonDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOrbitDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexOutpostDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPlanetDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexPoiDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexSpaceStationDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexTerminalDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.model.Faction;
import de.greluc.krt.iri.basetool.backend.model.Jurisdiction;
import de.greluc.krt.iri.basetool.backend.model.Moon;
import de.greluc.krt.iri.basetool.backend.model.Orbit;
import de.greluc.krt.iri.basetool.backend.model.Outpost;
import de.greluc.krt.iri.basetool.backend.model.Planet;
import de.greluc.krt.iri.basetool.backend.model.Poi;
import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    // ─── syncCities — additional cases ──────────────────────────────────────

    @Test
    void syncCities_skipsDtoWithoutId() {
        // Given
        UexCityDto noId = UexCityDto.builder().name("Unknown").build();
        when(uexClient.getCities()).thenReturn(List.of(noId));

        // When
        service.syncCities();

        // Then
        verify(cityRepository, never()).save(any());
        verify(locationRepository, never()).save(any());
    }

    @Test
    void syncCities_doesNotMaterialiseLocation_whenCityNotAvailableLive() {
        // Given — a city flagged as not live; no location row must be touched
        UexCityDto offline = UexCityDto.builder()
                .id(2).name("Old Lorville").isAvailableLive(0).build();
        when(uexClient.getCities()).thenReturn(List.of(offline));
        when(cityRepository.findByIdCity(2)).thenReturn(Optional.empty());
        when(cityRepository.findByName("Old Lorville")).thenReturn(Optional.empty());
        when(cityRepository.save(any(City.class))).thenAnswer(invocation -> {
            City c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        // When
        service.syncCities();

        // Then
        verify(cityRepository, atLeastOnce()).save(any(City.class));
        verifyNoInteractions(locationRepository);
    }

    @Test
    void syncCities_linksByName_whenIdCityIsNewButNameMatches() {
        // Given an existing City with the same name but no idCity
        City existing = new City();
        existing.setId(UUID.randomUUID());
        existing.setName("Area18");

        UexCityDto dto = UexCityDto.builder().id(7).name("Area18").isAvailableLive(0).build();
        when(uexClient.getCities()).thenReturn(List.of(dto));
        when(cityRepository.findByIdCity(7)).thenReturn(Optional.empty());
        when(cityRepository.findByName("Area18")).thenReturn(Optional.of(existing));
        when(cityRepository.save(any(City.class))).thenAnswer(i -> i.getArgument(0));

        // When
        service.syncCities();

        // Then — idCity was backfilled on the existing entity
        ArgumentCaptor<City> cap = ArgumentCaptor.forClass(City.class);
        verify(cityRepository, atLeastOnce()).save(cap.capture());
        assertEquals(7, cap.getValue().getIdCity());
    }

    // ─── syncFactions ───────────────────────────────────────────────────────

    @Test
    void syncFactions_upsertsNewFaction() {
        UexFactionDto dto = UexFactionDto.builder()
                .id(1).name("UEE").code("UEE")
                .isAvailableLive(1).wiki("https://wiki/UEE")
                .isPiracy(0).isBountyHunting(1).build();
        when(uexClient.getFactions()).thenReturn(List.of(dto));
        when(factionRepository.findByIdFaction(1)).thenReturn(Optional.empty());
        when(factionRepository.findByName("UEE")).thenReturn(Optional.empty());
        when(factionRepository.save(any(Faction.class))).thenAnswer(i -> i.getArgument(0));

        service.syncFactions();

        ArgumentCaptor<Faction> cap = ArgumentCaptor.forClass(Faction.class);
        verify(factionRepository, atLeastOnce()).save(cap.capture());
        Faction saved = cap.getValue();
        assertEquals(1, saved.getIdFaction());
        assertEquals("UEE", saved.getName());
        assertEquals("UEE", saved.getCode());
        assertTrue(saved.getIsAvailableLive());
        assertFalse(saved.getIsPiracy());
        assertTrue(saved.getIsBountyHunting());
    }

    @Test
    void syncFactions_skipsDtoWithoutId() {
        UexFactionDto noId = UexFactionDto.builder().name("X").build();
        when(uexClient.getFactions()).thenReturn(List.of(noId));

        service.syncFactions();

        verify(factionRepository, never()).save(any());
    }

    @Test
    void syncFactions_emptyResponse_doesNotInvokeRepositories() {
        when(uexClient.getFactions()).thenReturn(List.of());
        service.syncFactions();
        verifyNoInteractions(factionRepository);
    }

    // ─── syncJurisdictions ──────────────────────────────────────────────────

    @Test
    void syncJurisdictions_upsertsNewJurisdiction() {
        UexJurisdictionDto dto = UexJurisdictionDto.builder()
                .id(10).name("Hurston Sec").code("HS").isAvailableLive(1)
                .nickname("HUR").wiki("https://wiki").factionName("HDC").build();
        when(uexClient.getJurisdictions()).thenReturn(List.of(dto));
        when(jurisdictionRepository.findByIdJurisdiction(10)).thenReturn(Optional.empty());
        when(jurisdictionRepository.findByName("Hurston Sec")).thenReturn(Optional.empty());
        when(jurisdictionRepository.save(any(Jurisdiction.class))).thenAnswer(i -> i.getArgument(0));

        service.syncJurisdictions();

        ArgumentCaptor<Jurisdiction> cap = ArgumentCaptor.forClass(Jurisdiction.class);
        verify(jurisdictionRepository, atLeastOnce()).save(cap.capture());
        assertEquals(10, cap.getValue().getIdJurisdiction());
        assertEquals("HUR", cap.getValue().getNickname());
        assertEquals("HDC", cap.getValue().getFactionName());
    }

    @Test
    void syncJurisdictions_emptyResponse_aborts() {
        when(uexClient.getJurisdictions()).thenReturn(List.of());
        service.syncJurisdictions();
        verifyNoInteractions(jurisdictionRepository);
    }

    // ─── syncMoons ──────────────────────────────────────────────────────────

    @Test
    void syncMoons_upsertsNewMoon() {
        UexMoonDto dto = UexMoonDto.builder()
                .id(20).name("Lyria").code("LYR").isAvailableLive(1)
                .isAvailable(1).isVisible(1).isDefault(0)
                .starSystemName("Stanton").planetName("Crusader")
                .build();
        when(uexClient.getMoons()).thenReturn(List.of(dto));
        when(moonRepository.findByIdMoon(20)).thenReturn(Optional.empty());
        when(moonRepository.findByName("Lyria")).thenReturn(Optional.empty());
        when(moonRepository.save(any(Moon.class))).thenAnswer(i -> i.getArgument(0));

        service.syncMoons();

        ArgumentCaptor<Moon> cap = ArgumentCaptor.forClass(Moon.class);
        verify(moonRepository, atLeastOnce()).save(cap.capture());
        assertEquals(20, cap.getValue().getIdMoon());
        assertTrue(cap.getValue().getIsAvailable());
        assertTrue(cap.getValue().getIsVisible());
        assertFalse(cap.getValue().getIsDefault());
    }

    @Test
    void syncMoons_emptyResponse_aborts() {
        when(uexClient.getMoons()).thenReturn(List.of());
        service.syncMoons();
        verifyNoInteractions(moonRepository);
    }

    // ─── syncOrbits ─────────────────────────────────────────────────────────

    @Test
    void syncOrbits_upsertsNewOrbit() {
        UexOrbitDto dto = UexOrbitDto.builder()
                .id(30).name("HurstonOrbit").code("HO").isAvailableLive(1)
                .starSystemName("Stanton").factionName("UEE").build();
        when(uexClient.getOrbits()).thenReturn(List.of(dto));
        when(orbitRepository.findByIdOrbit(30)).thenReturn(Optional.empty());
        when(orbitRepository.findByName("HurstonOrbit")).thenReturn(Optional.empty());
        when(orbitRepository.save(any(Orbit.class))).thenAnswer(i -> i.getArgument(0));

        service.syncOrbits();

        ArgumentCaptor<Orbit> cap = ArgumentCaptor.forClass(Orbit.class);
        verify(orbitRepository, atLeastOnce()).save(cap.capture());
        assertEquals(30, cap.getValue().getIdOrbit());
        assertEquals("UEE", cap.getValue().getFactionName());
    }

    @Test
    void syncOrbits_emptyResponse_aborts() {
        when(uexClient.getOrbits()).thenReturn(List.of());
        service.syncOrbits();
        verifyNoInteractions(orbitRepository);
    }

    // ─── syncOutposts ───────────────────────────────────────────────────────

    @Test
    void syncOutposts_upsertsNewOutpost() {
        UexOutpostDto dto = UexOutpostDto.builder()
                .id(40).name("HDMS-Anderson").code("HDMS")
                .isAvailableLive(1).isAvailable(1)
                .padTypes("M,L").nickname("HDMS-A")
                .build();
        when(uexClient.getOutposts()).thenReturn(List.of(dto));
        when(outpostRepository.findByIdOutpost(40)).thenReturn(Optional.empty());
        when(outpostRepository.findByName("HDMS-Anderson")).thenReturn(Optional.empty());
        when(outpostRepository.save(any(Outpost.class))).thenAnswer(i -> i.getArgument(0));

        service.syncOutposts();

        ArgumentCaptor<Outpost> cap = ArgumentCaptor.forClass(Outpost.class);
        verify(outpostRepository, atLeastOnce()).save(cap.capture());
        assertEquals(40, cap.getValue().getIdOutpost());
        assertEquals("M,L", cap.getValue().getPadTypes());
    }

    @Test
    void syncOutposts_emptyResponse_aborts() {
        when(uexClient.getOutposts()).thenReturn(List.of());
        service.syncOutposts();
        verifyNoInteractions(outpostRepository);
    }

    // ─── syncPlanets ────────────────────────────────────────────────────────

    @Test
    void syncPlanets_upsertsNewPlanet() {
        UexPlanetDto dto = UexPlanetDto.builder()
                .id(50).name("Hurston").code("HUR")
                .isAvailableLive(1).isAvailable(1).isVisible(1).isDefault(0)
                .starSystemName("Stanton").build();
        when(uexClient.getPlanets()).thenReturn(List.of(dto));
        when(planetRepository.findByIdPlanet(50)).thenReturn(Optional.empty());
        when(planetRepository.findByName("Hurston")).thenReturn(Optional.empty());
        when(planetRepository.save(any(Planet.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPlanets();

        ArgumentCaptor<Planet> cap = ArgumentCaptor.forClass(Planet.class);
        verify(planetRepository, atLeastOnce()).save(cap.capture());
        assertEquals(50, cap.getValue().getIdPlanet());
        assertTrue(cap.getValue().getIsAvailable());
        assertFalse(cap.getValue().getIsDefault());
    }

    @Test
    void syncPlanets_emptyResponse_aborts() {
        when(uexClient.getPlanets()).thenReturn(List.of());
        service.syncPlanets();
        verifyNoInteractions(planetRepository);
    }

    // ─── syncPois ───────────────────────────────────────────────────────────

    @Test
    void syncPois_upsertsNewPoi() {
        UexPoiDto dto = UexPoiDto.builder()
                .id(60).name("Klescher").isAvailableLive(1)
                .isAvailable(1).build();
        when(uexClient.getPoi()).thenReturn(List.of(dto));
        when(poiRepository.findByIdPoi(60)).thenReturn(Optional.empty());
        when(poiRepository.findByName("Klescher")).thenReturn(Optional.empty());
        when(poiRepository.save(any(Poi.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPois();

        ArgumentCaptor<Poi> cap = ArgumentCaptor.forClass(Poi.class);
        verify(poiRepository, atLeastOnce()).save(cap.capture());
        assertEquals(60, cap.getValue().getIdPoi());
    }

    @Test
    void syncPois_emptyResponse_aborts() {
        when(uexClient.getPoi()).thenReturn(List.of());
        service.syncPois();
        verifyNoInteractions(poiRepository);
    }

    // ─── syncSpaceStations ──────────────────────────────────────────────────

    @Test
    void syncSpaceStations_upsertsAndMaterializesLocation_whenAvailableLive() {
        // Given a live space station
        UexSpaceStationDto dto = UexSpaceStationDto.builder()
                .id(70).name("Port Olisar").code("POL").isAvailableLive(1)
                .isAvailable(1).isLagrange(1).build();
        when(uexClient.getSpaceStations()).thenReturn(List.of(dto));
        when(spaceStationRepository.findByIdSpaceStation(70)).thenReturn(Optional.empty());
        when(spaceStationRepository.findByName("Port Olisar")).thenReturn(Optional.empty());
        when(spaceStationRepository.save(any(SpaceStation.class))).thenAnswer(i -> {
            SpaceStation s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(locationRepository.findBySpaceStationId(any())).thenReturn(Optional.empty());
        when(locationRepository.findByName("Port Olisar")).thenReturn(Optional.empty());
        when(locationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        service.syncSpaceStations();

        // Then
        verify(spaceStationRepository, atLeastOnce()).save(any(SpaceStation.class));
        verify(locationRepository, atLeastOnce()).save(any());
    }

    @Test
    void syncSpaceStations_doesNotMaterializeLocation_whenNotAvailableLive() {
        UexSpaceStationDto offline = UexSpaceStationDto.builder()
                .id(71).name("Decommissioned Station").isAvailableLive(0)
                .build();
        when(uexClient.getSpaceStations()).thenReturn(List.of(offline));
        when(spaceStationRepository.findByIdSpaceStation(71)).thenReturn(Optional.empty());
        when(spaceStationRepository.findByName("Decommissioned Station")).thenReturn(Optional.empty());
        when(spaceStationRepository.save(any(SpaceStation.class))).thenAnswer(i -> {
            SpaceStation s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        service.syncSpaceStations();

        verify(spaceStationRepository, atLeastOnce()).save(any());
        verifyNoInteractions(locationRepository);
    }

    @Test
    void syncSpaceStations_emptyResponse_aborts() {
        when(uexClient.getSpaceStations()).thenReturn(List.of());
        service.syncSpaceStations();
        verifyNoInteractions(spaceStationRepository, locationRepository);
    }

    // ─── syncTerminals ──────────────────────────────────────────────────────

    @Test
    void syncTerminals_upsertsNewTerminal() {
        UexTerminalDto dto = UexTerminalDto.builder()
                .id(80).name("Lorville TDD").code("LV-TDD").isAvailableLive(1)
                .nickname("TDD LV").starSystemName("Stanton").planetName("Hurston")
                .cityName("Lorville").build();
        when(uexClient.getTerminals()).thenReturn(List.of(dto));
        when(terminalRepository.findByIdTerminal(80)).thenReturn(Optional.empty());
        when(terminalRepository.findByName("Lorville TDD")).thenReturn(Optional.empty());
        when(terminalRepository.save(any(Terminal.class))).thenAnswer(i -> i.getArgument(0));

        service.syncTerminals();

        ArgumentCaptor<Terminal> cap = ArgumentCaptor.forClass(Terminal.class);
        verify(terminalRepository, atLeastOnce()).save(cap.capture());
        assertEquals(80, cap.getValue().getIdTerminal());
        assertEquals("TDD LV", cap.getValue().getNickname());
        assertEquals("Lorville", cap.getValue().getCityName());
    }

    @Test
    void syncTerminals_emptyResponse_aborts() {
        when(uexClient.getTerminals()).thenReturn(List.of());
        service.syncTerminals();
        verifyNoInteractions(terminalRepository);
    }
}
