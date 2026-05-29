package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemManufacturerDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ScWikiItemSyncService} Mode B — the R5 full Wiki item backfill
 * (SC_WIKI_SYNC_PLAN.md §8.4). Covers mode selection, per-endpoint kind derivation, {@code
 * WIKI_ONLY} creation, the {@code UEX_ONLY → BOTH} flip, the more-specific-wins kind tie-breaker,
 * the §3.4 sanity-cap guard, the junk-name guard, manufacturer resolution and the cross-kind
 * orphan-sweep gating.
 */
@ExtendWith(MockitoExtension.class)
class ScWikiItemSyncServiceBackfillTest {

  private static final String WEAPON_ATTACHMENTS = "/api/weapon-attachments";
  private static final String WEAPONS = "/api/weapons";
  private static final String VEHICLE_WEAPONS = "/api/vehicle-weapons";
  private static final String VEHICLE_ITEMS = "/api/vehicle-items";
  private static final String ARMOR = "/api/armor";
  private static final String CLOTHES = "/api/clothes";
  private static final String FOOD = "/api/food";
  private static final String ITEMS = "/api/items";

  @Mock private ScWikiClient scWikiClient;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private BlueprintRepository blueprintRepository;
  @Mock private ManufacturerRepository manufacturerRepository;
  @Mock private SyncReportService syncReportService;
  @Mock private EntityManager entityManager;

  private ScWikiProperties properties;
  private ScWikiItemSyncService service;

  @BeforeEach
  void setUp() {
    properties = new ScWikiProperties();
    properties.setItemSyncEnabled(true);
    properties.setSyncAllItems(true);
    service =
        new ScWikiItemSyncService(
            scWikiClient,
            properties,
            gameItemRepository,
            blueprintRepository,
            manufacturerRepository,
            syncReportService,
            entityManager);
    lenient().when(syncReportService.beginRun()).thenReturn(UUID.randomUUID());
  }

  @Test
  void flushAndClearIfBatchFull_flushesAndClearsAtTheBatchBoundary() {
    service.flushAndClearIfBatchFull(ScWikiItemSyncService.FLUSH_BATCH_SIZE);

    verify(entityManager).flush();
    verify(entityManager).clear();
  }

  @Test
  void flushAndClearIfBatchFull_doesNothingBetweenBoundaries() {
    service.flushAndClearIfBatchFull(0);
    service.flushAndClearIfBatchFull(ScWikiItemSyncService.FLUSH_BATCH_SIZE - 1);
    service.flushAndClearIfBatchFull(ScWikiItemSyncService.FLUSH_BATCH_SIZE + 1);

    org.mockito.Mockito.verifyNoInteractions(entityManager);
  }

  // ---- mode selection -------------------------------------------------------------------------

  @Test
  void syncItems_dispatchesToBackfill_whenSyncAllItemsTrue() {
    stubPass(WEAPONS, itemDto(UUID.randomUUID(), "Behring P4-AR"));

    service.syncItems();

    // Mode B walks list endpoints; it must never fall back to the per-UUID closure fetch.
    verify(scWikiClient, never()).fetchOne(any(), any(), any());
    verify(scWikiClient).fetchAllPages(eq(WEAPONS), any(), any(), any(), any());
  }

  @Test
  void syncItems_dispatchesToClosure_whenSyncAllItemsFalse() {
    properties.setSyncAllItems(false);
    UUID uuid = UUID.randomUUID();
    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of(uuid));
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of());
    when(scWikiClient.fetchOne(any(), eq(ScWikiItemDto.class), any())).thenReturn(null);

    service.syncItems();

    verify(scWikiClient).fetchOne(eq(ITEMS + "/" + uuid), eq(ScWikiItemDto.class), any());
    verify(scWikiClient, never()).fetchAllPages(any(), any(), any(), any(), any());
  }

  @Test
  void syncItems_isNoOp_whenFeatureFlagOff_evenWithSyncAllItemsTrue() {
    properties.setItemSyncEnabled(false);

    service.syncItems();

    verify(scWikiClient, never()).fetchAllPages(any(), any(), any(), any(), any());
    verify(scWikiClient, never()).fetchOne(any(), any(), any());
  }

  // ---- per-endpoint kind derivation + WIKI_ONLY creation ---------------------------------------

  @Test
  void backfill_derivesKindFromSourceEndpoint_andCreatesWikiOnlyRows() {
    UUID armorUuid = UUID.randomUUID();
    UUID weaponUuid = UUID.randomUUID();
    stubPass(ARMOR, itemDto(armorUuid, "Pembroke Helmet"));
    stubPass(WEAPONS, itemDto(weaponUuid, "Gallant Rifle"));
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());

    service.syncItems();

    Map<UUID, GameItem> saved = captureSaves();
    assertEquals(GameItemKind.ARMOR, saved.get(armorUuid).getKind());
    assertEquals(GameItemSourceSystem.WIKI_ONLY, saved.get(armorUuid).getSourceSystems());
    assertEquals(GameItemKind.WEAPON, saved.get(weaponUuid).getKind());
    assertEquals(GameItemSourceSystem.WIKI_ONLY, saved.get(weaponUuid).getSourceSystems());
    // The new row records a CREATED_WIKI_ONLY finding.
    verify(syncReportService)
        .logScwikiEvent(
            any(),
            eq(SyncEventType.CREATED_WIKI_ONLY),
            eq("game_item"),
            eq(armorUuid),
            any(),
            any());
  }

  @Test
  void backfill_residualItemsPassCreatesGenericRows() {
    UUID cargoUuid = UUID.randomUUID();
    stubPass(ITEMS, itemDto(cargoUuid, "Titanium Crate"));
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());

    service.syncItems();

    assertEquals(GameItemKind.GENERIC, captureSaves().get(cargoUuid).getKind());
  }

  // ---- existing-row flip + canonical-field protection ------------------------------------------

  @Test
  void backfill_flipsUexOnlyToBoth_andFillsWikiColumns_withoutOverwritingCanonicalFields() {
    UUID uuid = UUID.randomUUID();
    Manufacturer uexMfr = manufacturer("UEX Aegis");
    GameItem existing = new GameItem();
    existing.setExternalUuid(uuid);
    existing.setName("UEX Canonical Name");
    existing.setKind(GameItemKind.GENERIC);
    existing.setManufacturer(uexMfr);
    existing.setSourceSystems(GameItemSourceSystem.UEX_ONLY);

    stubPass(VEHICLE_ITEMS, itemDto(uuid, "Wiki Display Name"));
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.of(existing));

    service.syncItems();

    GameItem result = captureSaves().get(uuid);
    assertEquals(GameItemSourceSystem.BOTH, result.getSourceSystems());
    assertEquals(GameItemKind.VEHICLE_ITEM, result.getKind()); // GENERIC upgraded
    assertEquals("UEX Canonical Name", result.getName()); // UEX name preserved
    assertSame(uexMfr, result.getManufacturer()); // UEX manufacturer sticky
    assertEquals("classif", result.getClassification()); // Wiki column filled
  }

  @Test
  void backfill_neverDowngradesAMoreSpecificExistingKind() {
    UUID uuid = UUID.randomUUID();
    GameItem existing = new GameItem();
    existing.setExternalUuid(uuid);
    existing.setName("Size 3 Cannon");
    existing.setKind(GameItemKind.VEHICLE_WEAPON);
    existing.setSourceSystems(GameItemSourceSystem.UEX_ONLY);

    // The vehicle-items pass would assign VEHICLE_ITEM, which is LESS specific than VEHICLE_WEAPON.
    stubPass(VEHICLE_ITEMS, itemDto(uuid, "Cannon"));
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.of(existing));

    service.syncItems();

    assertEquals(GameItemKind.VEHICLE_WEAPON, captureSaves().get(uuid).getKind());
  }

  // ---- §3.4 sanity-cap guard ------------------------------------------------------------------

  @Test
  void backfill_skipsKindPassThatExceedsSanityCap_butStillRunsOtherPasses() {
    properties.setBackfillKindSanityCap(2);
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID weaponUuid = UUID.randomUUID();
    // Armor comes back pool-sized (3 > cap 2) — the §3.4 full-pool quirk; it must be skipped whole.
    lenient()
        .when(scWikiClient.fetchAllPages(eq(ARMOR), any(), any(), any(), any()))
        .thenReturn(List.of(itemDto(a, "A"), itemDto(b, "B"), itemDto(c, "C")));
    stubPass(WEAPONS, itemDto(weaponUuid, "Rifle"));
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());

    service.syncItems();

    Map<UUID, GameItem> saved = captureSaves();
    assertNull(saved.get(a), "capped armor rows must not be ingested");
    assertEquals(GameItemKind.WEAPON, saved.get(weaponUuid).getKind());
    // A capped pass counts as a failure → orphan sweep is suppressed.
    verify(gameItemRepository, never()).markScwikiDeletedExcept(any(), any());
  }

  // ---- junk-name guard ------------------------------------------------------------------------

  @Test
  void backfill_skipsJunkNamedNewRows_andLogsSkipJunk() {
    UUID uuid = UUID.randomUUID();
    stubPass(WEAPONS, itemDto(uuid, "<= PLACEHOLDER =>"));
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.empty());

    service.syncItems();

    verify(gameItemRepository, never()).save(any());
    verify(syncReportService)
        .logScwikiEvent(
            any(), eq(SyncEventType.SKIP_JUNK), eq("game_item"), eq(uuid), any(), any());
  }

  // ---- manufacturer resolution for new rows ----------------------------------------------------

  @Test
  void backfill_resolvesManufacturerForNewRow_byNameAgainstExistingRowsOnly() {
    UUID uuid = UUID.randomUUID();
    Manufacturer aegis = manufacturer("Aegis Dynamics");
    when(manufacturerRepository.findAll()).thenReturn(List.of(aegis));
    ScWikiItemManufacturerDto mfrRef =
        new ScWikiItemManufacturerDto(UUID.randomUUID(), "aegis dynamics", "AEGS");
    stubPass(WEAPONS, itemDto(uuid, "Gallant", "Cargo", mfrRef));
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.empty());

    service.syncItems();

    assertSame(aegis, captureSaves().get(uuid).getManufacturer());
  }

  @Test
  void backfill_leavesManufacturerNull_whenNoExistingMatch_neverCreatesStub() {
    UUID uuid = UUID.randomUUID();
    ScWikiItemManufacturerDto mfrRef =
        new ScWikiItemManufacturerDto(UUID.randomUUID(), "Unknown Corp", "UNK");
    stubPass(WEAPONS, itemDto(uuid, "Mystery Gun", "Cargo", mfrRef));
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.empty());

    service.syncItems();

    assertNull(captureSaves().get(uuid).getManufacturer());
    verify(manufacturerRepository, never()).save(any());
  }

  // ---- cross-kind orphan sweep gating ----------------------------------------------------------

  @Test
  void backfill_runsOrphanSweep_onlyWhenEveryPassReturnedData() {
    stubPass(WEAPON_ATTACHMENTS, itemDto(UUID.randomUUID(), "Scope"));
    stubPass(WEAPONS, itemDto(UUID.randomUUID(), "Rifle"));
    stubPass(VEHICLE_WEAPONS, itemDto(UUID.randomUUID(), "Cannon"));
    stubPass(VEHICLE_ITEMS, itemDto(UUID.randomUUID(), "Cooler"));
    stubPass(ARMOR, itemDto(UUID.randomUUID(), "Helmet"));
    stubPass(CLOTHES, itemDto(UUID.randomUUID(), "Jacket"));
    stubPass(FOOD, itemDto(UUID.randomUUID(), "Ration"));
    stubPass(ITEMS, itemDto(UUID.randomUUID(), "Crate"));
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());

    service.syncItems();

    // All eight passes (7 kinds + GENERIC residual) returned data → the cross-kind sweep fires.
    verify(gameItemRepository).markScwikiDeletedExcept(any(), any());
  }

  @Test
  void backfill_skipsOrphanSweep_whenOneKindReturnsEmpty() {
    // All passes but FOOD return data; FOOD is left unstubbed → empty → suppresses the sweep.
    stubPass(WEAPON_ATTACHMENTS, itemDto(UUID.randomUUID(), "Scope"));
    stubPass(WEAPONS, itemDto(UUID.randomUUID(), "Rifle"));
    stubPass(VEHICLE_WEAPONS, itemDto(UUID.randomUUID(), "Cannon"));
    stubPass(VEHICLE_ITEMS, itemDto(UUID.randomUUID(), "Cooler"));
    stubPass(ARMOR, itemDto(UUID.randomUUID(), "Helmet"));
    stubPass(CLOTHES, itemDto(UUID.randomUUID(), "Jacket"));
    stubPass(ITEMS, itemDto(UUID.randomUUID(), "Crate"));
    when(gameItemRepository.findByExternalUuid(any())).thenReturn(Optional.empty());

    service.syncItems();

    verify(gameItemRepository, never()).markScwikiDeletedExcept(any(), any());
  }

  // ---- helpers ---------------------------------------------------------------------------------

  /**
   * Stubs the 5-arg {@code fetchAllPages} for a single endpoint to return exactly the given row.
   *
   * @param endpoint the endpoint to stub
   * @param row the single row the pass returns
   */
  private void stubPass(String endpoint, ScWikiItemDto row) {
    // Lenient: Mode B always pages every endpoint, so the per-endpoint stubs that a given test does
    // not exercise (other kinds return the default empty list) must not trip strict stubbing.
    lenient()
        .when(scWikiClient.fetchAllPages(eq(endpoint), any(), any(), any(), any()))
        .thenReturn(List.of(row));
  }

  /**
   * Captures every {@link GameItem} passed to {@code gameItemRepository.save}, keyed by external
   * UUID for direct per-item assertions.
   *
   * @return the saved game items keyed by {@code external_uuid}
   */
  private Map<UUID, GameItem> captureSaves() {
    ArgumentCaptor<GameItem> captor = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().stream()
        .collect(Collectors.toMap(GameItem::getExternalUuid, gi -> gi, (first, second) -> second));
  }

  /**
   * Builds a minimal valid Wiki item payload with a clean name and no manufacturer.
   *
   * @param uuid the asset UUID
   * @param name the display name
   * @return the payload
   */
  private static ScWikiItemDto itemDto(UUID uuid, String name) {
    return itemDto(uuid, name, "Cargo", null);
  }

  /**
   * Builds a Wiki item payload with an explicit Wiki {@code type} and manufacturer reference.
   *
   * @param uuid the asset UUID
   * @param name the display name
   * @param type the Wiki type token
   * @param manufacturer the nested manufacturer reference, or {@code null}
   * @return the payload
   */
  private static ScWikiItemDto itemDto(
      UUID uuid, String name, String type, ScWikiItemManufacturerDto manufacturer) {
    return new ScWikiItemDto(
        uuid,
        "slug-" + name,
        name,
        "class_name",
        "classif",
        "classifLabel",
        type,
        "typeLabel",
        "subType",
        "subTypeLabel",
        "1",
        "A",
        "common",
        1.0,
        null,
        manufacturer,
        Map.of("en_EN", "desc"),
        Boolean.TRUE,
        Boolean.FALSE,
        "4.8.0-LIVE");
  }

  /**
   * Builds a detached manufacturer with the given name (no-op identity for {@code assertSame}).
   *
   * @param name the manufacturer name
   * @return the manufacturer
   */
  private static Manufacturer manufacturer(String name) {
    Manufacturer m = new Manufacturer();
    m.setName(name);
    return m;
  }
}
