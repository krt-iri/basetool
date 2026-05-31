package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemMaterial;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.AggregatedMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.GameItemReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives, snapshots, aggregates and maps the material side of {@code ITEM} job orders.
 *
 * <p>The single source of truth for "which materials does a finished item need" is the SC-Wiki
 * blueprint graph ({@link Blueprint} + {@link BlueprintIngredient}). At create time this service
 * reads the chosen blueprint's RESOURCE ingredients, scales each by the ordered amount, applies the
 * requester's per-material Gut/Keine choice (defaulting from the ingredient's {@code minQuality}),
 * and snapshots the result onto the order so it stays stable even if the wiki data later changes.
 * The amount's unit follows the material's {@link QuantityType}: PIECE quantities are kept whole,
 * SCU quantities stay fractional. ITEM (sub-assembly) ingredients are intentionally NOT recursed
 * into materials here — they surface to the create UI as separate adoptable lines (see issue #304
 * decision 1), so each adopted sub-item line contributes its own RESOURCE materials.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderItemService {

  /**
   * Refining-grade quality threshold: an ingredient {@code minQuality} at or above this maps to
   * {@link QualityRequirement#GOOD}.
   */
  private static final int GOOD_QUALITY_THRESHOLD = 700;

  private final BlueprintRepository blueprintRepository;
  private final GameItemRepository gameItemRepository;
  private final MaterialMapper materialMapper;

  /**
   * Builds one ordered-item line with its derived, snapshotted material requirements. The returned
   * {@link JobOrderItem} is detached (not yet attached to an order) — the caller wires it onto the
   * {@link JobOrder} and resolves any sub-assembly parent link.
   *
   * @param line the create payload for this line (game item, chosen blueprint, amount, quality
   *     choices)
   * @return a populated {@link JobOrderItem} with its {@link JobOrderItemMaterial} children
   * @throws NotFoundException when the game item or blueprint id is unknown
   * @throws BadRequestException when the chosen blueprint does not produce the ordered game item
   */
  @NotNull
  public JobOrderItem buildItemLine(@NotNull CreateJobOrderItemLineDto line) {
    GameItem gameItem =
        gameItemRepository
            .findById(line.gameItemId())
            .orElseThrow(() -> new NotFoundException("GameItem not found: " + line.gameItemId()));
    Blueprint blueprint =
        blueprintRepository
            .findById(line.blueprintId())
            .orElseThrow(() -> new NotFoundException("Blueprint not found: " + line.blueprintId()));

    if (blueprint.getOutputItem() == null
        || !gameItem.getId().equals(blueprint.getOutputItem().getId())) {
      throw new BadRequestException(
          "Blueprint " + line.blueprintId() + " does not produce game item " + line.gameItemId());
    }

    JobOrderItem item =
        JobOrderItem.builder()
            .gameItem(gameItem)
            .blueprint(blueprint)
            .amount(line.amount())
            .build();

    Map<UUID, QualityRequirement> qualityChoices = qualityChoicesByMaterial(line.materials());

    for (BlueprintIngredient ingredient : blueprint.getIngredients()) {
      if (ingredient.getKind() != BlueprintIngredientKind.RESOURCE
          || ingredient.getMaterial() == null) {
        // ITEM ingredients become separate adoptable lines (decision 1); unresolved RESOURCE lines
        // (material == null) cannot be snapshotted and surface only as a create-time warning.
        continue;
      }
      Material material = ingredient.getMaterial();
      double perUnit = ingredient.getQuantityScu() == null ? 0.0 : ingredient.getQuantityScu();
      double required = roundForQuantityType(perUnit * line.amount(), material);
      QualityRequirement quality =
          qualityChoices.getOrDefault(material.getId(), defaultQuality(ingredient.getMinQuality()));

      item.addMaterial(
          JobOrderItemMaterial.builder()
              .material(material)
              .requiredQuantity(required)
              .qualityRequirement(quality)
              .build());
    }
    return item;
  }

  /**
   * Maps an item order's ordered lines to DTOs, sorted by item name then id for a stable table.
   *
   * @param order the (item) job order whose {@code items} to project
   * @return the ordered-item line DTOs; empty for a material order
   */
  @NotNull
  public List<JobOrderItemDto> toItemDtos(@NotNull JobOrder order) {
    return order.getItems().stream()
        .sorted(
            Comparator.<JobOrderItem, String>comparing(
                    i -> i.getGameItem() != null ? i.getGameItem().getName() : "",
                    String.CASE_INSENSITIVE_ORDER)
                .thenComparing(i -> i.getId().toString()))
        .map(this::toItemDto)
        .toList();
  }

  /**
   * Aggregates every snapshotted material requirement across all ordered lines into one row per
   * {@code (material, quality)}, summing the quantities. SCU materials sort first, then by name,
   * then GOOD before NONE — matching the material-handover table ordering.
   *
   * @param order the (item) job order to aggregate
   * @return the aggregated material rows; empty for a material order
   */
  @NotNull
  public List<AggregatedMaterialDto> aggregateMaterials(@NotNull JobOrder order) {
    record Key(UUID materialId, QualityRequirement quality) {}

    Map<Key, Double> sums = new LinkedHashMap<>();
    Map<UUID, Material> materials = new LinkedHashMap<>();
    for (JobOrderItem item : order.getItems()) {
      for (JobOrderItemMaterial req : item.getMaterials()) {
        Material material = req.getMaterial();
        Key key = new Key(material.getId(), req.getQualityRequirement());
        sums.merge(
            key, req.getRequiredQuantity() == null ? 0.0 : req.getRequiredQuantity(), Double::sum);
        materials.putIfAbsent(material.getId(), material);
      }
    }
    return sums.entrySet().stream()
        .map(
            e ->
                new AggregatedMaterialDto(
                    materialMapper.toDto(materials.get(e.getKey().materialId())),
                    e.getKey().quality(),
                    e.getValue()))
        .sorted(
            Comparator.<AggregatedMaterialDto, Integer>comparing(
                    a ->
                        a.material() != null && "SCU".equalsIgnoreCase(a.material().quantityType())
                            ? 0
                            : 1)
                .thenComparing(
                    a ->
                        a.material() != null && a.material().name() != null
                            ? a.material().name()
                            : "",
                    String.CASE_INSENSITIVE_ORDER)
                .thenComparing(a -> a.qualityRequirement().name()))
        .toList();
  }

  private JobOrderItemDto toItemDto(JobOrderItem item) {
    List<JobOrderItemMaterialDto> materials =
        item.getMaterials().stream()
            .sorted(
                Comparator.comparing(
                    m -> m.getMaterial() != null ? m.getMaterial().getName() : "",
                    String.CASE_INSENSITIVE_ORDER))
            .map(
                m ->
                    new JobOrderItemMaterialDto(
                        m.getId(),
                        materialMapper.toDto(m.getMaterial()),
                        m.getRequiredQuantity(),
                        m.getQualityRequirement(),
                        m.getVersion()))
            .toList();
    GameItem gameItem = item.getGameItem();
    Blueprint blueprint = item.getBlueprint();
    return new JobOrderItemDto(
        item.getId(),
        gameItem == null
            ? null
            : new GameItemReferenceDto(
                gameItem.getId(),
                gameItem.getName(),
                gameItem.getKind() == null ? null : gameItem.getKind().name()),
        blueprint == null
            ? null
            : new BlueprintReferenceDto(
                blueprint.getId(), blueprint.getOutputName(), blueprint.getScwikiKey()),
        item.getAmount(),
        item.getDeliveredAmount(),
        item.getParentItem() == null ? null : item.getParentItem().getId(),
        materials,
        item.getVersion());
  }

  private static Map<UUID, QualityRequirement> qualityChoicesByMaterial(
      List<CreateJobOrderItemMaterialDto> choices) {
    Map<UUID, QualityRequirement> map = new LinkedHashMap<>();
    if (choices != null) {
      for (CreateJobOrderItemMaterialDto choice : choices) {
        map.put(choice.materialId(), choice.quality());
      }
    }
    return map;
  }

  private static QualityRequirement defaultQuality(Integer minQuality) {
    return minQuality != null && minQuality >= GOOD_QUALITY_THRESHOLD
        ? QualityRequirement.GOOD
        : QualityRequirement.NONE;
  }

  private static double roundForQuantityType(double quantity, Material material) {
    return material != null && material.getQuantityType() == QuantityType.PIECE
        ? Math.round(quantity)
        : quantity;
  }

  /**
   * Resolves the available blueprint references for a game item, newest output name first. Exposed
   * for the create UI's blueprint picker.
   *
   * @param gameItemId the ordered item
   * @return blueprint references producing that item; empty when none exist
   */
  @NotNull
  public List<BlueprintReferenceDto> blueprintsForItem(@NotNull UUID gameItemId) {
    return blueprintRepository.findByOutputItemId(gameItemId).stream()
        .map(b -> new BlueprintReferenceDto(b.getId(), b.getOutputName(), b.getScwikiKey()))
        .sorted(
            Comparator.comparing(
                (BlueprintReferenceDto b) -> b.outputName() != null ? b.outputName() : "",
                String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
