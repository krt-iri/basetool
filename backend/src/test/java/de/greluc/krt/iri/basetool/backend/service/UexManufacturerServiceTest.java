package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UexManufacturerService}.
 *
 * <p>R2 expansion: the service now persists item manufacturers alongside vehicle manufacturers (R2
 * ships the UEX item catalogue, so both surfaces need rows). Matching is by UEX company id first,
 * falling back to case-insensitive name for legacy rows created before V107 added the {@code
 * uex_company_id} column.
 */
@ExtendWith(MockitoExtension.class)
class UexManufacturerServiceTest {

  @Mock private UexClient uexClient;

  @Mock private ManufacturerRepository manufacturerRepository;

  @InjectMocks private UexManufacturerService uexManufacturerService;

  @Test
  void persistsBothVehicleAndItemManufacturers_andWritesR2CrossRefColumns() {
    UexCompanyDto vehicleDto =
        UexCompanyDto.builder()
            .id(1)
            .name("Aegis Dynamics")
            .nickname("AEGS")
            .industry("Aerospace")
            .wiki("wiki-link")
            .isVehicleManufacturer(1)
            .build();
    UexCompanyDto itemDto =
        UexCompanyDto.builder()
            .id(2)
            .name("Casaba Outlet")
            .nickname("Casaba")
            .industry("Fashion")
            .isItemManufacturer(1)
            .isVehicleManufacturer(0)
            .build();

    when(uexClient.getCompanies()).thenReturn(List.of(vehicleDto, itemDto));
    when(manufacturerRepository.findByUexCompanyId(1)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByUexCompanyId(2)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Casaba Outlet")).thenReturn(Optional.empty());

    uexManufacturerService.syncManufacturers();

    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(2)).save(captor.capture());

    Manufacturer aegis = captor.getAllValues().get(0);
    assertEquals("Aegis Dynamics", aegis.getName());
    assertEquals("AEGS", aegis.getAbbreviation());
    assertEquals(1, aegis.getUexCompanyId());
    assertEquals("Aerospace", aegis.getIndustry());
    assertTrue(aegis.getIsVehicleManufacturer());
    assertFalse(aegis.getIsItemManufacturer());
    assertNotNull(aegis.getUexSyncedAt());

    Manufacturer casaba = captor.getAllValues().get(1);
    assertEquals("Casaba Outlet", casaba.getName());
    assertEquals(2, casaba.getUexCompanyId());
    assertTrue(casaba.getIsItemManufacturer());
    assertFalse(casaba.getIsVehicleManufacturer());
  }

  @Test
  void matchByUexCompanyId_shortCircuitsTheNameFallback() {
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(42)
            .name("Aegis Dynamics")
            .nickname("AEGS-New")
            .industry("Aerospace-New")
            .isVehicleManufacturer(1)
            .build();
    Manufacturer existing = new Manufacturer();
    existing.setName("Aegis Dynamics");
    existing.setAbbreviation("AEGS-Old");
    existing.setUexCompanyId(42);

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(42)).thenReturn(Optional.of(existing));

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository).findByUexCompanyId(42);
    verify(manufacturerRepository, never()).findByNameIgnoreCase(any());
    assertEquals("AEGS-New", existing.getAbbreviation());
    assertEquals("Aerospace-New", existing.getIndustry());
  }

  @Test
  void nameFallback_fires_whenUexCompanyIdNotYetPopulated_onLegacyRow() {
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(42)
            .name("Aegis Dynamics")
            .nickname("AEGS")
            .industry("Aerospace")
            .isVehicleManufacturer(1)
            .build();
    Manufacturer legacy = new Manufacturer();
    legacy.setName("Aegis Dynamics");
    legacy.setAbbreviation("AEGS");
    // legacy: no uexCompanyId populated yet (pre-V107)

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(42)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics"))
        .thenReturn(Optional.of(legacy));

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository).save(legacy);
    assertEquals(42, legacy.getUexCompanyId(), "name-fallback hit must backfill uex_company_id");
  }

  @Test
  void abbreviationFallback_adoptsLegacyShortNamedRow_insteadOfInsertingDuplicateAbbreviation() {
    // UEX returns the full company name, but the legacy vehicle-manufacturer row was seeded with
    // its short nickname as the name: local "Esperia" vs UEX name "Esperia Incorporation" /
    // nickname "Esperia". Both the id and name lookups miss, so without the abbreviation fallback
    // the insert collides on the UNIQUE manufacturer.abbreviation ("Esperia" already exists) and
    // poisons the whole single-transaction sweep.
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(278)
            .name("Esperia Incorporation")
            .nickname("Esperia")
            .industry("Aerospace")
            .isItemManufacturer(1)
            .isVehicleManufacturer(1)
            .build();
    Manufacturer legacy = new Manufacturer();
    legacy.setName("Esperia");
    legacy.setAbbreviation("Esperia");
    // legacy: seeded before this sync persisted every company, so no uexCompanyId yet.

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(278)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Esperia Incorporation"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findByAbbreviationIgnoreCase("Esperia"))
        .thenReturn(Optional.of(legacy));

    uexManufacturerService.syncManufacturers();

    // Adopted the existing row: exactly one save, and it is the legacy instance — no duplicate row.
    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(1)).save(captor.capture());
    Manufacturer saved = captor.getValue();
    assertSame(legacy, saved, "must adopt the abbreviation-matched row, not insert a new one");
    assertEquals(
        278, saved.getUexCompanyId(), "abbreviation-fallback hit must backfill uex_company_id");
    assertEquals("Esperia", saved.getAbbreviation(), "the UNIQUE abbreviation key is preserved");
    assertEquals(
        "Esperia Incorporation", saved.getName(), "name is updated to the UEX-canonical full name");
  }

  @Test
  void emptyResponse_skipsWrites() {
    when(uexClient.getCompanies()).thenReturn(List.of());

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository, never()).save(any());
  }
}
