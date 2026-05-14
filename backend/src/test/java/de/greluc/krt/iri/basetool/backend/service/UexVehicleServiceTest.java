package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexVehicleDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UexVehicleService}. UEX returns the full vehicle list once per scheduler
 * tick (see {@link UexScheduler}); the service has to upsert matching ship types in our DB while
 * gracefully tolerating partial payloads.
 *
 * <p>The contract under test:
 *
 * <ul>
 *   <li>An empty / null response from UEX must NOT wipe the local table — the service aborts early.
 *   <li>Vehicles without a {@code name} must be skipped (foreign-key constraint violation
 *       otherwise).
 *   <li>Manufacturers are looked up case-insensitively by company name; a missing manufacturer is
 *       non-fatal — the ship type still saves.
 *   <li>A name match against an existing {@link ShipType} updates in place (preserving the entity
 *       id / version); no match creates a new row.
 *   <li>Description is built up only from fields that are actually present so we don't pollute the
 *       DB with "Crew: null" lines.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UexVehicleServiceTest {

  @Mock private UexClient uexClient;
  @Mock private ShipTypeRepository shipTypeRepository;
  @Mock private ManufacturerRepository manufacturerRepository;

  @InjectMocks private UexVehicleService service;

  @BeforeEach
  void resetMocks() {
    // Stub a "fresh DB" baseline; individual tests override where needed.
  }

  @Test
  void syncVehicles_withEmptyUexResponse_shouldAbortWithoutTouchingRepositories() {
    // Given
    when(uexClient.getVehicles()).thenReturn(List.of());

    // When
    service.syncVehicles();

    // Then
    verify(uexClient).getVehicles();
    verifyNoInteractions(shipTypeRepository, manufacturerRepository);
  }

  @Test
  void syncVehicles_skipsVehiclesWithoutName() {
    // Given
    UexVehicleDto noName =
        new UexVehicleDto(null, "Full Name", "Drake Interplanetary", 46, "1-4", "", "", 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(noName));

    // When
    service.syncVehicles();

    // Then
    verify(uexClient).getVehicles();
    verifyNoInteractions(shipTypeRepository, manufacturerRepository);
  }

  @Test
  void syncVehicles_skipsVehiclesWithBlankName() {
    // Given
    UexVehicleDto blank = new UexVehicleDto("   ", "Full Name", "Drake", 46, "1", null, null, 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(blank));

    // When
    service.syncVehicles();

    // Then
    verifyNoInteractions(shipTypeRepository, manufacturerRepository);
  }

  @Test
  void syncVehicles_createsNewShipType_whenNameDoesNotExist() {
    // Given
    UUID manufacturerId = UUID.randomUUID();
    Manufacturer drake = new Manufacturer();
    drake.setId(manufacturerId);
    drake.setName("Drake Interplanetary");

    UexVehicleDto cutlass =
        new UexVehicleDto(
            "Cutlass Black",
            "Drake Cutlass Black",
            "Drake Interplanetary",
            46,
            "1-2",
            "https://store/cutlass",
            "https://wiki/cutlass",
            1,
            0);
    when(uexClient.getVehicles()).thenReturn(List.of(cutlass));
    when(manufacturerRepository.findByNameIgnoreCase("Drake Interplanetary"))
        .thenReturn(Optional.of(drake));
    when(shipTypeRepository.findByNameIgnoreCase("Cutlass Black")).thenReturn(Optional.empty());

    // When
    service.syncVehicles();

    // Then
    ArgumentCaptor<ShipType> saved = ArgumentCaptor.forClass(ShipType.class);
    verify(shipTypeRepository).save(saved.capture());
    ShipType created = saved.getValue();
    assertEquals("Cutlass Black", created.getName());
    assertEquals(46, created.getScu());
    assertSame(drake, created.getManufacturer());
    assertNotNull(created.getDescription());
    assertTrue(created.getDescription().contains("Full Name: Drake Cutlass Black"));
    assertTrue(created.getDescription().contains("SCU: 46"));
    assertTrue(created.getDescription().contains("Crew: 1-2"));
    // wiki takes precedence over store URL
    assertTrue(created.getDescription().contains("Wiki: https://wiki/cutlass"));
    assertFalse(created.getDescription().contains("Store:"));
  }

  @Test
  void syncVehicles_updatesExistingShipType_inPlace_preservingId() {
    // Given an existing row with the same name (case-insensitive) and a stale SCU
    UUID existingId = UUID.randomUUID();
    ShipType existing = new ShipType();
    existing.setId(existingId);
    existing.setName("Caterpillar");
    existing.setScu(0);
    existing.setVersion(3L);

    Manufacturer drake = new Manufacturer();
    drake.setName("Drake");

    UexVehicleDto cat =
        new UexVehicleDto(
            "Caterpillar",
            "Drake Caterpillar",
            "Drake",
            576,
            "4-6",
            "https://store/cat",
            null,
            1,
            0);
    when(uexClient.getVehicles()).thenReturn(List.of(cat));
    when(manufacturerRepository.findByNameIgnoreCase("Drake")).thenReturn(Optional.of(drake));
    when(shipTypeRepository.findByNameIgnoreCase("Caterpillar")).thenReturn(Optional.of(existing));

    // When
    service.syncVehicles();

    // Then — same instance saved, mutated in place
    ArgumentCaptor<ShipType> saved = ArgumentCaptor.forClass(ShipType.class);
    verify(shipTypeRepository).save(saved.capture());
    assertSame(
        existing, saved.getValue(), "Existing entity must be mutated in place, NOT replaced");
    assertEquals(existingId, saved.getValue().getId());
    assertEquals(
        3L, saved.getValue().getVersion(), "Version must remain — JPA handles optimistic locking");
    assertEquals(576, saved.getValue().getScu());
    assertSame(drake, saved.getValue().getManufacturer());
    // wiki absent → store URL used as fallback
    assertTrue(saved.getValue().getDescription().contains("Store: https://store/cat"));
    assertFalse(saved.getValue().getDescription().contains("Wiki:"));
  }

  @Test
  void syncVehicles_savesWithoutManufacturer_whenLookupFails() {
    // Given — UEX returns a company we don't have in our DB
    UexVehicleDto orphan =
        new UexVehicleDto("Mystery Ship", null, "UnknownCorp", 10, null, null, null, 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(orphan));
    when(manufacturerRepository.findByNameIgnoreCase("UnknownCorp")).thenReturn(Optional.empty());
    when(shipTypeRepository.findByNameIgnoreCase("Mystery Ship")).thenReturn(Optional.empty());

    // When
    service.syncVehicles();

    // Then — the ship type is still saved, just without a manufacturer
    ArgumentCaptor<ShipType> saved = ArgumentCaptor.forClass(ShipType.class);
    verify(shipTypeRepository).save(saved.capture());
    assertEquals("Mystery Ship", saved.getValue().getName());
    assertNull(saved.getValue().getManufacturer());
    assertEquals(10, saved.getValue().getScu());
  }

  @Test
  void syncVehicles_skipsManufacturerLookup_whenCompanyNameMissing() {
    // Given
    UexVehicleDto noCompany =
        new UexVehicleDto("Indie Ship", null, null, 5, null, null, null, 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(noCompany));
    when(shipTypeRepository.findByNameIgnoreCase("Indie Ship")).thenReturn(Optional.empty());

    // When
    service.syncVehicles();

    // Then — manufacturer repo never touched, ship type still saved
    verify(manufacturerRepository, never()).findByNameIgnoreCase(any());
    ArgumentCaptor<ShipType> saved = ArgumentCaptor.forClass(ShipType.class);
    verify(shipTypeRepository).save(saved.capture());
    assertNull(saved.getValue().getManufacturer());
  }

  @Test
  void syncVehicles_buildsEmptyDescription_whenAllDescriptionFieldsBlank() {
    // Given a payload that wouldn't add anything meaningful to the description
    UexVehicleDto bare = new UexVehicleDto("Bare Ship", null, null, null, null, null, null, 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(bare));
    when(shipTypeRepository.findByNameIgnoreCase("Bare Ship")).thenReturn(Optional.empty());

    // When
    service.syncVehicles();

    // Then — description not set when there's nothing to write
    ArgumentCaptor<ShipType> saved = ArgumentCaptor.forClass(ShipType.class);
    verify(shipTypeRepository).save(saved.capture());
    assertNull(
        saved.getValue().getDescription(),
        "When the dto carries no description-contributing fields, description must stay null");
  }

  @Test
  void syncVehicles_savesEveryVehicleInBatch_independently() {
    // Given — one new, one update, one skipped (no name)
    UexVehicleDto a = new UexVehicleDto("A", null, null, 1, null, null, null, 1, 0);
    UexVehicleDto b = new UexVehicleDto("B", null, null, 2, null, null, null, 1, 0);
    UexVehicleDto skip = new UexVehicleDto(null, null, null, 0, null, null, null, 1, 0);
    when(uexClient.getVehicles()).thenReturn(List.of(a, b, skip));

    ShipType existingB = new ShipType();
    existingB.setId(UUID.randomUUID());
    existingB.setName("B");

    when(shipTypeRepository.findByNameIgnoreCase("A")).thenReturn(Optional.empty());
    when(shipTypeRepository.findByNameIgnoreCase("B")).thenReturn(Optional.of(existingB));

    // When
    service.syncVehicles();

    // Then — exactly two saves (a + b), the skip never reached the repo
    verify(shipTypeRepository, times(2)).save(any(ShipType.class));
    verify(shipTypeRepository, never()).findByNameIgnoreCase(eq(null));
  }
}
