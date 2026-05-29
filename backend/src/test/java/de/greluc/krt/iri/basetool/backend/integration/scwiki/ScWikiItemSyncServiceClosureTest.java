package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiDimensionDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ScWikiItemSyncService} — the R4 closure-mode item fill. */
@ExtendWith(MockitoExtension.class)
class ScWikiItemSyncServiceClosureTest {

  @Mock private ScWikiClient scWikiClient;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private BlueprintRepository blueprintRepository;
  @Mock private ManufacturerRepository manufacturerRepository;
  @Mock private SyncReportService syncReportService;

  private ScWikiProperties properties;
  private ScWikiItemSyncService service;

  @BeforeEach
  void setUp() {
    properties = new ScWikiProperties();
    properties.setItemSyncEnabled(true);
    service =
        new ScWikiItemSyncService(
            scWikiClient,
            properties,
            gameItemRepository,
            blueprintRepository,
            manufacturerRepository,
            syncReportService);
    lenient().when(syncReportService.beginRun()).thenReturn(UUID.randomUUID());
  }

  @Test
  void syncItems_isNoOp_whenFeatureFlagOff() {
    properties.setItemSyncEnabled(false);

    service.syncItems();

    verifyNoInteractions(scWikiClient, gameItemRepository, blueprintRepository);
  }

  @Test
  void syncItems_pullsOnlyExistingUuidsPlusBlueprintRefs_neverEnumeratesFullList() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of(a));
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of(b));
    when(scWikiClient.fetchOne(any(), eq(ScWikiItemDto.class), any())).thenReturn(null);

    service.syncItems();

    // Closure mode hits GET /api/items/{uuid} exactly for the two scoped uuids — never a list walk.
    verify(scWikiClient).fetchOne(eq("/api/items/" + a), eq(ScWikiItemDto.class), any());
    verify(scWikiClient).fetchOne(eq("/api/items/" + b), eq(ScWikiItemDto.class), any());
    verify(scWikiClient, never()).fetchAllPages(any(), any(), any());
  }

  @Test
  void syncItems_fillsWikiColumnsAndFlipsUexOnlyToBoth() {
    UUID uuid = UUID.randomUUID();
    GameItem existing = new GameItem();
    existing.setId(UUID.randomUUID());
    existing.setExternalUuid(uuid);
    existing.setName("Venture Helmet");
    existing.setKind(GameItemKind.ARMOR);
    existing.setSourceSystems(GameItemSourceSystem.UEX_ONLY);

    ScWikiItemDto dto =
        new ScWikiItemDto(
            uuid,
            "venture-helmet-white-2",
            "Venture Helmet White",
            "rsi_explorer_helmet",
            "FPS.Armor.Helmet",
            "Helmet",
            "Char_Armor",
            "Armor",
            "Helmet",
            "Helmet",
            "1",
            "A",
            "common",
            2.5,
            new ScWikiDimensionDto(0.3, 0.4, 0.35),
            null,
            Map.of("en_EN", "An explorer helmet.", "de_DE", "Ein Forscherhelm."),
            true,
            false,
            "4.8.0-LIVE");

    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of(uuid));
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of());
    when(scWikiClient.fetchOne(eq("/api/items/" + uuid), eq(ScWikiItemDto.class), any()))
        .thenReturn(dto);
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.of(existing));
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    GameItem result = saved.getValue();
    assertEquals(GameItemSourceSystem.BOTH, result.getSourceSystems());
    assertEquals("venture-helmet-white-2", result.getScwikiSlug());
    assertEquals("FPS.Armor.Helmet", result.getClassification());
    assertEquals(2.5, result.getMass());
    assertEquals(0.3, result.getDimensionX());
    assertEquals("An explorer helmet.", result.getDescriptionEn());
    assertEquals("Ein Forscherhelm.", result.getDescriptionDe());
    assertEquals(1, result.getSizeClass());
    // UEX-canonical fields untouched.
    assertEquals("Venture Helmet", result.getName());
    assertEquals(GameItemKind.ARMOR, result.getKind());
  }

  @Test
  void syncItems_createsWikiOnlyRow_forBlueprintRefNotInGameItem() {
    UUID uuid = UUID.randomUUID();
    ScWikiItemDto dto =
        new ScWikiItemDto(
            uuid,
            "hadanite",
            "Hadanite",
            "hadanite_cls",
            "Mineral",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("en_EN", "A mineral."),
            false,
            true,
            "4.8");
    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of());
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of(uuid));
    when(scWikiClient.fetchOne(eq("/api/items/" + uuid), eq(ScWikiItemDto.class), any()))
        .thenReturn(dto);
    when(gameItemRepository.findByExternalUuid(uuid)).thenReturn(Optional.empty());
    when(gameItemRepository.save(any(GameItem.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncItems();

    ArgumentCaptor<GameItem> saved = ArgumentCaptor.forClass(GameItem.class);
    verify(gameItemRepository).save(saved.capture());
    GameItem created = saved.getValue();
    assertEquals(uuid, created.getExternalUuid());
    assertEquals("Hadanite", created.getName());
    assertEquals(GameItemKind.GENERIC, created.getKind());
    assertEquals(GameItemSourceSystem.WIKI_ONLY, created.getSourceSystems());
  }

  @Test
  void syncItems_emitsWikiMissing_whenFetchReturnsNull() {
    UUID uuid = UUID.randomUUID();
    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of(uuid));
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of());
    when(scWikiClient.fetchOne(any(), eq(ScWikiItemDto.class), any())).thenReturn(null);

    service.syncItems();

    verify(syncReportService)
        .logScwikiEvent(
            any(), eq(SyncEventType.WIKI_MISSING), eq("game_item"), eq(uuid), any(), any());
    verify(gameItemRepository, never()).save(any());
  }

  @Test
  void syncItems_noTargets_isNoOp() {
    when(gameItemRepository.findAllExternalUuids()).thenReturn(List.of());
    when(blueprintRepository.findReferencedItemUuids()).thenReturn(List.of());

    service.syncItems();

    verify(scWikiClient, never()).fetchOne(any(), any(), any());
  }
}
