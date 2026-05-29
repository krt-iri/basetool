package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexItemDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.UexCategory;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
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

/** Unit tests for {@link UexItemSyncService}. */
@ExtendWith(MockitoExtension.class)
class UexItemSyncServiceTest {

  @Mock private UexClient uexClient;
  @Mock private UexCategoryRefService categoryRefService;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private ManufacturerRepository manufacturerRepository;
  @Mock private ShipTypeRepository shipTypeRepository;

  @InjectMocks private UexItemSyncService service;

  private UexCategory helmetsCategory;
  private UexCategory liveriesCategory;
  private Manufacturer rsi;

  @BeforeEach
  void setUp() {
    helmetsCategory = new UexCategory();
    helmetsCategory.setId(3);
    helmetsCategory.setType("item");
    helmetsCategory.setSection("Armor");
    helmetsCategory.setName("Helmets");
    helmetsCategory.setIsGameRelated(true);
    helmetsCategory.setIsMining(false);

    liveriesCategory = new UexCategory();
    liveriesCategory.setId(75);
    liveriesCategory.setType("item");
    liveriesCategory.setSection("Liveries");
    liveriesCategory.setName("Paints");
    liveriesCategory.setIsGameRelated(true);
    liveriesCategory.setIsMining(false);

    rsi = new Manufacturer();
    rsi.setId(UUID.randomUUID());
    rsi.setName("Roberts Space Industries");
    rsi.setUexCompanyId(1);
  }

  @Test
  void syncItems_persistsUexColumnsAndStampsUexOnlySource_whenUUIDPresent() {
    UexItemDto helmet =
        new UexItemDto(
            42,
            0,
            3,
            1,
            0,
            "Venture Helmet White",
            "venture-helmet-white-2",
            "28c76343-8da9-495a-9339-3d5de02e6c3c",
            "1",
            "white",
            null,
            0,
            "https://example.com/store",
            "Armor",
            "Helmets",
            "Roberts Space Industries",
            null,
            "https://example.com/shot.png",
            0,
            0,
            0,
            0,
            0,
            "4.8.0-LIVE",
            123L,
            456L,
            null);

    when(categoryRefService.syncCategories()).thenReturn(List.of(helmetsCategory));
    when(uexClient.getItemsForCategory(3)).thenReturn(List.of(helmet));
    when(gameItemRepository.findByUexItemId(42)).thenReturn(Optional.empty());
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());
    when(manufacturerRepository.findByUexCompanyId(1)).thenReturn(Optional.of(rsi));
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    GameItem persisted = saved.getValue();
    assertEquals(GameItemKind.ARMOR, persisted.getKind());
    assertEquals(GameItemSourceSystem.UEX_ONLY, persisted.getSourceSystems());
    assertEquals(
        UUID.fromString("28c76343-8da9-495a-9339-3d5de02e6c3c"), persisted.getExternalUuid());
    assertEquals(42, persisted.getUexItemId());
    assertEquals("venture-helmet-white-2", persisted.getUexSlug());
    assertSame(rsi, persisted.getManufacturer());
    assertEquals("Venture Helmet White", persisted.getName());
    assertNotNull(persisted.getUexSyncedAt());
    // Wiki columns stay untouched (R2 does not write them).
    assertNull(persisted.getScwikiSyncedAt());
    assertNull(persisted.getDescriptionEn());
  }

  @Test
  void syncItems_handlesEmptyUuidByLeavingExternalUuidNull() {
    UexItemDto avionics = helmetDto(99, "Random Flight Blade", "", helmetsCategory);
    // shift category section to Avionics for this case
    helmetsCategory.setSection("Avionics");
    helmetsCategory.setName("Flight Blade");

    when(categoryRefService.syncCategories()).thenReturn(List.of(helmetsCategory));
    when(uexClient.getItemsForCategory(3)).thenReturn(List.of(avionics));
    when(gameItemRepository.findByUexItemId(99)).thenReturn(Optional.empty());
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    GameItem persisted = saved.getValue();
    assertNull(persisted.getExternalUuid());
    assertEquals(99, persisted.getUexItemId());
    assertEquals(GameItemKind.VEHICLE_ITEM, persisted.getKind());
  }

  @Test
  void syncItems_promotesWikiOnlyToBothWhenUexLandsOnExistingExternalUuid() {
    UUID externalUuid = UUID.randomUUID();
    UexItemDto helmet = helmetDto(7, "Existing Helmet", externalUuid.toString(), helmetsCategory);

    GameItem prior = new GameItem();
    prior.setId(UUID.randomUUID());
    prior.setExternalUuid(externalUuid);
    prior.setName("Existing Helmet");
    prior.setKind(GameItemKind.ARMOR);
    prior.setSourceSystems(GameItemSourceSystem.WIKI_ONLY);
    prior.setScwikiSyncedAt(java.time.Instant.now());

    when(categoryRefService.syncCategories()).thenReturn(List.of(helmetsCategory));
    when(uexClient.getItemsForCategory(3)).thenReturn(List.of(helmet));
    when(gameItemRepository.findByUexItemId(7)).thenReturn(Optional.empty());
    when(gameItemRepository.findByExternalUuid(externalUuid)).thenReturn(Optional.of(prior));
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    assertEquals(GameItemSourceSystem.BOTH, saved.getValue().getSourceSystems());
    assertNotNull(saved.getValue().getScwikiSyncedAt(), "Wiki timestamp must be preserved");
  }

  @Test
  void syncItems_doesNotDowngradeKindToGeneric_whenUexReCataloguesAWikiSpecificRow() {
    // A paint Wiki filed as VEHICLE_ITEM (via /vehicle-items); UEX later lists the same
    // external_uuid under the "Liveries" section (deriveKind → GENERIC). The §6.3.1
    // more-specific-wins merge must keep VEHICLE_ITEM — UEX must not downgrade it to GENERIC.
    UUID externalUuid = UUID.randomUUID();
    UexItemDto paint =
        helmetDto(21, "100i Auspicious Red Dog Livery", externalUuid.toString(), liveriesCategory);

    GameItem prior = new GameItem();
    prior.setId(UUID.randomUUID());
    prior.setExternalUuid(externalUuid);
    prior.setName("100i Auspicious Red Dog Livery");
    prior.setKind(GameItemKind.VEHICLE_ITEM);
    prior.setSourceSystems(GameItemSourceSystem.WIKI_ONLY);
    prior.setScwikiSyncedAt(java.time.Instant.now());

    when(categoryRefService.syncCategories()).thenReturn(List.of(liveriesCategory));
    when(uexClient.getItemsForCategory(75)).thenReturn(List.of(paint));
    when(gameItemRepository.findByUexItemId(21)).thenReturn(Optional.empty());
    when(gameItemRepository.findByExternalUuid(externalUuid)).thenReturn(Optional.of(prior));
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        saved.getValue().getKind(),
        "UEX must not downgrade a Wiki-set VEHICLE_ITEM to GENERIC (§6.3.1)");
  }

  @Test
  void syncItems_skipsOrphanSweep_whenNoItemsWereProcessed() {
    when(categoryRefService.syncCategories()).thenReturn(List.of(helmetsCategory));
    when(uexClient.getItemsForCategory(3)).thenReturn(List.of());

    service.syncItems();

    verify(gameItemRepository, never()).markUexDeletedExcept(any(), any());
  }

  @Test
  void syncItems_runsOrphanSweep_whenAtLeastOneItemProcessed() {
    UexItemDto helmet =
        helmetDto(11, "Venture Helmet", UUID.randomUUID().toString(), helmetsCategory);
    when(categoryRefService.syncCategories()).thenReturn(List.of(helmetsCategory));
    when(uexClient.getItemsForCategory(3)).thenReturn(List.of(helmet));
    when(gameItemRepository.findByUexItemId(anyInt())).thenReturn(Optional.empty());
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));
    when(gameItemRepository.markUexDeletedExcept(any(), any())).thenReturn(0);

    service.syncItems();

    verify(gameItemRepository).markUexDeletedExcept(any(), any());
  }

  @Test
  void deriveKind_armorSection_returnsArmor() {
    UexCategory armor = newCategory("Armor", "Helmets");
    assertEquals(GameItemKind.ARMOR, UexItemSyncService.deriveKind(armor));
  }

  @Test
  void deriveKind_personalWeaponsWithAttachmentsName_returnsWeaponAttachment() {
    UexCategory attachments = newCategory("Personal Weapons", "Attachments");
    assertEquals(GameItemKind.WEAPON_ATTACHMENT, UexItemSyncService.deriveKind(attachments));
  }

  @Test
  void deriveKind_personalWeaponsRifles_returnsWeapon() {
    UexCategory rifles = newCategory("Personal Weapons", "Rifles");
    assertEquals(GameItemKind.WEAPON, UexItemSyncService.deriveKind(rifles));
  }

  @Test
  void deriveKind_vehicleWeapons_returnsVehicleWeapon() {
    UexCategory weapons = newCategory("Vehicle Weapons", "Guns");
    assertEquals(GameItemKind.VEHICLE_WEAPON, UexItemSyncService.deriveKind(weapons));
  }

  @Test
  void deriveKind_systemsAvionicsUtility_returnsVehicleItem() {
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        UexItemSyncService.deriveKind(newCategory("Systems", "Coolers")));
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        UexItemSyncService.deriveKind(newCategory("Avionics", "Flight Blade")));
    assertEquals(
        GameItemKind.VEHICLE_ITEM,
        UexItemSyncService.deriveKind(newCategory("Utility", "Quantum Drives")));
  }

  @Test
  void deriveKind_clothingOrUndersuits_returnsClothing() {
    assertEquals(
        GameItemKind.CLOTHING, UexItemSyncService.deriveKind(newCategory("Clothing", "Jackets")));
    assertEquals(
        GameItemKind.CLOTHING,
        UexItemSyncService.deriveKind(newCategory("Undersuits", "Standard")));
  }

  @Test
  void deriveKind_unknownSection_returnsGeneric() {
    assertEquals(
        GameItemKind.GENERIC,
        UexItemSyncService.deriveKind(newCategory("MysterySection", "Whatever")));
  }

  private UexCategory newCategory(String section, String name) {
    UexCategory c = new UexCategory();
    c.setSection(section);
    c.setName(name);
    return c;
  }

  private UexItemDto helmetDto(int id, String name, String uuid, UexCategory cat) {
    return new UexItemDto(
        id,
        0,
        cat.getId() == null ? 3 : cat.getId(),
        1,
        0,
        name,
        name.toLowerCase().replace(' ', '-'),
        uuid,
        "1",
        null,
        null,
        0,
        null,
        cat.getSection(),
        cat.getName(),
        "Roberts Space Industries",
        null,
        null,
        0,
        0,
        0,
        0,
        0,
        "4.8.0-LIVE",
        0L,
        0L,
        null);
  }
}
