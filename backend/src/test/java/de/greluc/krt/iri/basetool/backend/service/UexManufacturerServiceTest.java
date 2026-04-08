package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UexManufacturerServiceTest {

    @Mock
    private UexClient uexClient;

    @Mock
    private ManufacturerRepository manufacturerRepository;

    @InjectMocks
    private UexManufacturerService uexManufacturerService;

    @Test
    void shouldFilterAndCreateNewManufacturer() {
        // Given
        UexCompanyDto vehicleDto = UexCompanyDto.builder()
                .id(1)
                .name("Aegis Dynamics")
                .nickname("AEGS")
                .industry("Aerospace")
                .wiki("wiki-link")
                .isVehicleManufacturer(1)
                .build();

        UexCompanyDto nonVehicleDto = UexCompanyDto.builder()
                .id(2)
                .name("Casaba Outlet")
                .nickname("Casaba")
                .isVehicleManufacturer(0)
                .build();

        when(uexClient.getCompanies()).thenReturn(List.of(vehicleDto, nonVehicleDto));
        when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics")).thenReturn(Optional.empty());

        // When
        uexManufacturerService.syncManufacturers();

        // Then
        ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
        verify(manufacturerRepository, times(1)).save(captor.capture());
        
        Manufacturer saved = captor.getValue();
        assertEquals("Aegis Dynamics", saved.getName());
        assertEquals("AEGS", saved.getAbbreviation());
        assertTrue(saved.getDescription().contains("Industry: Aerospace"));
        assertTrue(saved.getDescription().contains("Wiki: wiki-link"));
    }

    @Test
    void shouldUpdateExistingManufacturer() {
        // Given
        UexCompanyDto vehicleDto = UexCompanyDto.builder()
                .id(1)
                .name("Aegis Dynamics")
                .nickname("AEGS-New")
                .industry("Aerospace-New")
                .isVehicleManufacturer(1)
                .build();

        Manufacturer existing = new Manufacturer();
        existing.setName("Aegis Dynamics");
        existing.setAbbreviation("AEGS-Old");
        existing.setDescription("Old Description");

        when(uexClient.getCompanies()).thenReturn(List.of(vehicleDto));
        when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics")).thenReturn(Optional.of(existing));

        // When
        uexManufacturerService.syncManufacturers();

        // Then
        verify(manufacturerRepository, times(1)).save(existing);
        assertEquals("Aegis Dynamics", existing.getName());
        assertEquals("AEGS-New", existing.getAbbreviation());
        assertEquals("Industry: Aerospace-New", existing.getDescription());
    }
}
