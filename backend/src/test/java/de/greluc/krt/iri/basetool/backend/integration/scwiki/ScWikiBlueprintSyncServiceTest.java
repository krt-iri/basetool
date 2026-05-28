package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintIngredientDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.service.MaterialExternalAliasService;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ScWikiBlueprintSyncService} — the R4 blueprint recipe-graph sync. */
@ExtendWith(MockitoExtension.class)
class ScWikiBlueprintSyncServiceTest {

  @Mock private ScWikiClient scWikiClient;
  @Mock private BlueprintRepository blueprintRepository;
  @Mock private MaterialRepository materialRepository;
  @Mock private MaterialExternalAliasService aliasService;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private SyncReportService syncReportService;

  private ScWikiProperties properties;
  private ScWikiBlueprintSyncService service;

  @BeforeEach
  void setUp() {
    properties = new ScWikiProperties();
    properties.setBlueprintSyncEnabled(true);
    service =
        new ScWikiBlueprintSyncService(
            scWikiClient,
            properties,
            blueprintRepository,
            materialRepository,
            aliasService,
            gameItemRepository,
            syncReportService);
    lenient().when(syncReportService.beginRun()).thenReturn(UUID.randomUUID());
  }

  @Test
  void syncBlueprints_isNoOp_whenFeatureFlagOff() {
    properties.setBlueprintSyncEnabled(false);

    service.syncBlueprints();

    verifyNoInteractions(scWikiClient, blueprintRepository);
  }

  @Test
  void syncBlueprints_resolvesResourceToMaterialAndItemToGameItem() {
    UUID resourceUuid = UUID.randomUUID();
    UUID itemUuid = UUID.randomUUID();
    UUID outputUuid = UUID.randomUUID();
    ScWikiBlueprintIngredientDto resource =
        new ScWikiBlueprintIngredientDto("Agricium", "resource", resourceUuid, null, 0.36, null);
    ScWikiBlueprintIngredientDto item =
        new ScWikiBlueprintIngredientDto("Hadanite", "item", null, itemUuid, null, 7);
    ScWikiBlueprintDto dto = blueprint(outputUuid, List.of(resource, item), List.of());

    Material agricium = material("Agricium");
    GameItem hadanite = gameItem(itemUuid);
    GameItem output = gameItem(outputUuid);
    when(scWikiClient.fetchAllPages(any(), any(), eq("blueprints"))).thenReturn(List.of(dto));
    when(blueprintRepository.findByScwikiUuid(dto.uuid())).thenReturn(Optional.empty());
    when(gameItemRepository.findByExternalUuid(outputUuid)).thenReturn(Optional.of(output));
    when(materialRepository.findByScwikiUuid(resourceUuid)).thenReturn(Optional.of(agricium));
    when(gameItemRepository.findByExternalUuid(itemUuid)).thenReturn(Optional.of(hadanite));
    when(blueprintRepository.save(any(Blueprint.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncBlueprints();

    ArgumentCaptor<Blueprint> saved = ArgumentCaptor.forClass(Blueprint.class);
    verify(blueprintRepository).save(saved.capture());
    Blueprint bp = saved.getValue();
    assertSame(output, bp.getOutputItem());
    assertEquals(2, bp.getIngredients().size());

    BlueprintIngredient line0 = bp.getIngredients().get(0);
    assertEquals(BlueprintIngredientKind.RESOURCE, line0.getKind());
    assertSame(agricium, line0.getMaterial());
    assertNull(line0.getGameItem());
    assertEquals(0.36, line0.getQuantityScu());
    assertNull(line0.getQuantityUnits());
    assertEquals(resourceUuid, line0.getWikiResourceUuid());

    BlueprintIngredient line1 = bp.getIngredients().get(1);
    assertEquals(BlueprintIngredientKind.ITEM, line1.getKind());
    assertSame(hadanite, line1.getGameItem());
    assertNull(line1.getMaterial());
    assertEquals(7, line1.getQuantityUnits());
    assertNull(line1.getQuantityScu());
  }

  @Test
  void syncBlueprints_unresolvedIngredient_persistsSnapshotAndEmitsEvent() {
    UUID resourceUuid = UUID.randomUUID();
    ScWikiBlueprintIngredientDto resource =
        new ScWikiBlueprintIngredientDto("Unobtanium", "resource", resourceUuid, null, 1.0, null);
    ScWikiBlueprintDto dto = blueprint(null, List.of(resource), List.of());

    when(scWikiClient.fetchAllPages(any(), any(), eq("blueprints"))).thenReturn(List.of(dto));
    when(blueprintRepository.findByScwikiUuid(dto.uuid())).thenReturn(Optional.empty());
    when(materialRepository.findByScwikiUuid(resourceUuid)).thenReturn(Optional.empty());
    when(aliasService.resolveMaterialByAlias(any(), any())).thenReturn(null);
    when(materialRepository.findByName("Unobtanium")).thenReturn(Optional.empty());
    when(blueprintRepository.save(any(Blueprint.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncBlueprints();

    ArgumentCaptor<Blueprint> saved = ArgumentCaptor.forClass(Blueprint.class);
    verify(blueprintRepository).save(saved.capture());
    BlueprintIngredient line = saved.getValue().getIngredients().get(0);
    assertNull(line.getMaterial(), "unresolved RESOURCE keeps a null material FK");
    assertEquals(
        resourceUuid, line.getWikiResourceUuid(), "raw Wiki uuid is persisted for re-resolve");
    assertEquals("Unobtanium", line.getWikiNameSnapshot());
    verify(syncReportService)
        .logScwikiEvent(
            any(),
            eq(SyncEventType.UNRESOLVED_INGREDIENT),
            eq("blueprint"),
            any(),
            eq("Unobtanium"),
            any());
  }

  @Test
  void syncBlueprints_shrinkingIngredientCount_dropsTrailingLines() {
    UUID bpUuid = UUID.randomUUID();
    // Existing blueprint with 3 ingredient lines.
    Blueprint existing = new Blueprint();
    existing.setScwikiUuid(bpUuid);
    for (int i = 0; i < 3; i++) {
      BlueprintIngredient line = new BlueprintIngredient();
      line.setBlueprint(existing);
      line.setOrderIndex(i);
      line.setKind(BlueprintIngredientKind.RESOURCE);
      existing.getIngredients().add(line);
    }
    // Incoming DTO has only 1 ingredient.
    ScWikiBlueprintIngredientDto one =
        new ScWikiBlueprintIngredientDto("Iron", "resource", UUID.randomUUID(), null, 1.0, null);
    ScWikiBlueprintDto dto =
        new ScWikiBlueprintDto(
            bpUuid, "BP", null, "Out", null, 10, false, "4.8", 1, 0, List.of(one), List.of());

    when(scWikiClient.fetchAllPages(any(), any(), eq("blueprints"))).thenReturn(List.of(dto));
    when(blueprintRepository.findByScwikiUuid(bpUuid)).thenReturn(Optional.of(existing));
    when(materialRepository.findByScwikiUuid(any())).thenReturn(Optional.empty());
    when(aliasService.resolveMaterialByAlias(any(), any())).thenReturn(null);
    when(materialRepository.findByName(any())).thenReturn(Optional.empty());
    when(blueprintRepository.save(any(Blueprint.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncBlueprints();

    assertEquals(1, existing.getIngredients().size(), "trailing lines must be dropped");
  }

  @Test
  void syncBlueprints_emptyResponse_skipsOrphanSweep() {
    when(scWikiClient.fetchAllPages(any(), any(), eq("blueprints"))).thenReturn(List.of());

    service.syncBlueprints();

    verify(blueprintRepository, never()).markScwikiDeleted(any(), any());
    verify(blueprintRepository, never()).save(any());
  }

  @Test
  void syncBlueprints_runsOrphanSweep_whenAtLeastOneProcessed() {
    ScWikiBlueprintDto dto = blueprint(null, List.of(), List.of());
    when(scWikiClient.fetchAllPages(any(), any(), eq("blueprints"))).thenReturn(List.of(dto));
    when(blueprintRepository.findByScwikiUuid(dto.uuid())).thenReturn(Optional.empty());
    when(blueprintRepository.save(any(Blueprint.class))).thenAnswer(inv -> inv.getArgument(0));
    when(blueprintRepository.markScwikiDeleted(any(), any())).thenReturn(0);

    service.syncBlueprints();

    verify(blueprintRepository).markScwikiDeleted(any(), any());
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private ScWikiBlueprintDto blueprint(
      UUID outputUuid,
      List<ScWikiBlueprintIngredientDto> ingredients,
      List<ScWikiBlueprintIngredientDto> dismantle) {
    return new ScWikiBlueprintDto(
        UUID.randomUUID(),
        "BP_TEST",
        outputUuid,
        "Test Output",
        UUID.randomUUID(),
        540,
        false,
        "4.8.0-LIVE",
        ingredients.size(),
        0,
        ingredients,
        dismantle);
  }

  private Material material(String name) {
    Material m = new Material();
    m.setId(UUID.randomUUID());
    m.setName(name);
    return m;
  }

  private GameItem gameItem(UUID externalUuid) {
    GameItem g = new GameItem();
    g.setId(UUID.randomUUID());
    g.setExternalUuid(externalUuid);
    g.setName("Item " + externalUuid);
    return g;
  }
}
