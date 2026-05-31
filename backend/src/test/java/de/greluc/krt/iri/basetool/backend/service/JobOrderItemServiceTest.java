package de.greluc.krt.iri.basetool.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderType;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.AggregatedMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobOrderItemService}: blueprint-driven material derivation (amount scaling,
 * quality default vs override, quantity-type rounding, ignoring sub-assembly and unresolved
 * ingredients), the blueprint/item consistency check, and the per-order material aggregation.
 */
@ExtendWith(MockitoExtension.class)
class JobOrderItemServiceTest {

  @Mock private BlueprintRepository blueprintRepository;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private MaterialMapper materialMapper;
  @InjectMocks private JobOrderItemService service;

  @Test
  void buildItemLineDerivesResourceMaterialsScalingByAmountWithQualityDefaultAndOverride() {
    // Given a weapon blueprint with two RESOURCE ingredients plus an ITEM and an unresolved line.
    GameItem weapon = gameItem("Ballista", GameItemKind.WEAPON);
    Material steel = material("Steel", QuantityType.SCU);
    Material screws = material("Screws", QuantityType.PIECE);
    Blueprint blueprint = blueprint(weapon);
    blueprint.addIngredient(resource(steel, 2.5, 700)); // default GOOD (minQuality 700)
    blueprint.addIngredient(resource(screws, 4.0, null)); // default NONE
    blueprint.addIngredient(itemIngredient(gameItem("Scope", GameItemKind.WEAPON_ATTACHMENT), 1));
    blueprint.addIngredient(unresolvedResource(9.0)); // material == null, must be skipped

    when(gameItemRepository.findById(weapon.getId())).thenReturn(Optional.of(weapon));
    when(blueprintRepository.findById(blueprint.getId())).thenReturn(Optional.of(blueprint));

    // When ordering 3 units, overriding screws to GOOD and leaving steel at its default.
    CreateJobOrderItemLineDto line =
        new CreateJobOrderItemLineDto(
            weapon.getId(),
            blueprint.getId(),
            3,
            List.of(new CreateJobOrderItemMaterialDto(screws.getId(), QualityRequirement.GOOD)),
            null,
            null);
    JobOrderItem built = service.buildItemLine(line);

    // Then only the two resolved RESOURCE materials are snapshotted, scaled by the amount.
    assertThat(built.getAmount()).isEqualTo(3);
    assertThat(built.getDeliveredAmount()).isZero();
    assertThat(built.getMaterials()).hasSize(2);

    JobOrderItemMaterial steelReq = requirementFor(built, steel);
    assertThat(steelReq.getRequiredQuantity()).isEqualTo(7.5);
    assertThat(steelReq.getQualityRequirement()).isEqualTo(QualityRequirement.GOOD);

    JobOrderItemMaterial screwsReq = requirementFor(built, screws);
    assertThat(screwsReq.getRequiredQuantity()).isEqualTo(12.0);
    assertThat(screwsReq.getQualityRequirement()).isEqualTo(QualityRequirement.GOOD);
  }

  @Test
  void buildItemLineRoundsPieceQuantitiesToWholeNumbers() {
    GameItem item = gameItem("Crate", GameItemKind.GENERIC);
    Material bolts = material("Bolts", QuantityType.PIECE);
    Blueprint blueprint = blueprint(item);
    blueprint.addIngredient(resource(bolts, 2.5, null)); // 2.5 * 3 = 7.5 -> rounds to 8

    when(gameItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
    when(blueprintRepository.findById(blueprint.getId())).thenReturn(Optional.of(blueprint));

    JobOrderItem built =
        service.buildItemLine(
            new CreateJobOrderItemLineDto(
                item.getId(), blueprint.getId(), 3, List.of(), null, null));

    assertThat(requirementFor(built, bolts).getRequiredQuantity()).isEqualTo(8.0);
  }

  @Test
  void buildItemLineRejectsBlueprintThatDoesNotProduceTheItem() {
    GameItem ordered = gameItem("Ballista", GameItemKind.WEAPON);
    GameItem other = gameItem("Other", GameItemKind.WEAPON);
    Blueprint blueprint = blueprint(other); // outputs a different item

    when(gameItemRepository.findById(ordered.getId())).thenReturn(Optional.of(ordered));
    when(blueprintRepository.findById(blueprint.getId())).thenReturn(Optional.of(blueprint));

    CreateJobOrderItemLineDto line =
        new CreateJobOrderItemLineDto(ordered.getId(), blueprint.getId(), 1, List.of(), null, null);

    assertThatThrownBy(() -> service.buildItemLine(line))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not produce");
  }

  @Test
  void aggregateMaterialsGroupsByMaterialAndQualitySummingAcrossLines() {
    Material steel = material("Steel", QuantityType.SCU);
    stubMapper(steel);

    JobOrder order = JobOrder.builder().type(JobOrderType.ITEM).build();
    order.addItem(itemLine(steel, 5.0, QualityRequirement.GOOD));
    order.addItem(itemLine(steel, 3.0, QualityRequirement.GOOD));
    order.addItem(itemLine(steel, 2.0, QualityRequirement.NONE));

    List<AggregatedMaterialDto> aggregated = service.aggregateMaterials(order);

    // Two rows: Steel/GOOD = 8.0 (5+3), Steel/NONE = 2.0.
    assertThat(aggregated).hasSize(2);
    AggregatedMaterialDto good =
        aggregated.stream()
            .filter(a -> a.qualityRequirement() == QualityRequirement.GOOD)
            .findFirst()
            .orElseThrow();
    AggregatedMaterialDto none =
        aggregated.stream()
            .filter(a -> a.qualityRequirement() == QualityRequirement.NONE)
            .findFirst()
            .orElseThrow();
    assertThat(good.totalQuantity()).isEqualTo(8.0);
    assertThat(none.totalQuantity()).isEqualTo(2.0);
  }

  // ── helpers ──────────────────────────────────────────────────────────

  private void stubMapper(Material material) {
    lenient()
        .when(materialMapper.toDto(material))
        .thenReturn(
            new MaterialDto(
                material.getId(),
                material.getName(),
                null,
                material.getQuantityType().name(),
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
                null));
  }

  private JobOrderItem itemLine(Material material, double quantity, QualityRequirement quality) {
    JobOrderItem item = JobOrderItem.builder().amount(1).build();
    item.addMaterial(
        JobOrderItemMaterial.builder()
            .material(material)
            .requiredQuantity(quantity)
            .qualityRequirement(quality)
            .build());
    return item;
  }

  private static JobOrderItemMaterial requirementFor(JobOrderItem item, Material material) {
    return item.getMaterials().stream()
        .filter(m -> m.getMaterial().getId().equals(material.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no requirement for material " + material.getName()));
  }

  private static GameItem gameItem(String name, GameItemKind kind) {
    GameItem item = new GameItem();
    item.setId(UUID.randomUUID());
    item.setName(name);
    item.setKind(kind);
    return item;
  }

  private static Material material(String name, QuantityType quantityType) {
    Material material = new Material();
    material.setId(UUID.randomUUID());
    material.setName(name);
    material.setQuantityType(quantityType);
    return material;
  }

  private static Blueprint blueprint(GameItem output) {
    Blueprint blueprint = new Blueprint();
    blueprint.setId(UUID.randomUUID());
    blueprint.setOutputItem(output);
    blueprint.setOutputName(output.getName());
    blueprint.setScwikiKey(output.getName().toLowerCase());
    return blueprint;
  }

  private static BlueprintIngredient resource(
      Material material, double quantityScu, Integer minQuality) {
    BlueprintIngredient ingredient = new BlueprintIngredient();
    ingredient.setKind(BlueprintIngredientKind.RESOURCE);
    ingredient.setMaterial(material);
    ingredient.setQuantityScu(quantityScu);
    ingredient.setMinQuality(minQuality);
    return ingredient;
  }

  private static BlueprintIngredient unresolvedResource(double quantityScu) {
    BlueprintIngredient ingredient = new BlueprintIngredient();
    ingredient.setKind(BlueprintIngredientKind.RESOURCE);
    ingredient.setQuantityScu(quantityScu);
    ingredient.setWikiNameSnapshot("Unknownium");
    return ingredient;
  }

  private static BlueprintIngredient itemIngredient(GameItem gameItem, int quantityUnits) {
    BlueprintIngredient ingredient = new BlueprintIngredient();
    ingredient.setKind(BlueprintIngredientKind.ITEM);
    ingredient.setGameItem(gameItem);
    ingredient.setQuantityUnits(quantityUnits);
    return ingredient;
  }
}
