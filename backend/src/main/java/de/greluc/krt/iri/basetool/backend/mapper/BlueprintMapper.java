package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDismantleReturnDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintRequirementGroupDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintRequirementIngredientDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintRequirementModifierDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintRequirementModifierSegmentDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintSummaryPropertyDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintDismantleReturn;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintModifierSegment;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementGroup;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementModifier;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintSummaryProperty;
import java.util.List;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper from the blueprint recipe-graph entities to their read-side DTOs. Ingredient
 * names come from the always-present {@code wikiNameSnapshot} (so mapping a page never triggers a
 * material / game-item lazy load); the owned collections map element-wise via the per-type methods.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BlueprintMapper {

  /**
   * Maps a blueprint aggregate to its DTO, including the requirement groups (with their modifiers
   * and ingredients), the flat ingredient list, the summary-property roll-up and the dismantle
   * returns.
   *
   * @param entity the blueprint entity
   * @return the blueprint DTO
   */
  BlueprintDto toDto(Blueprint entity);

  /**
   * Maps a requirement group (build slot) to its DTO with nested modifiers and ingredients.
   *
   * @param group the requirement-group entity
   * @return the requirement-group DTO
   */
  BlueprintRequirementGroupDto toGroupDto(BlueprintRequirementGroup group);

  /**
   * Maps a requirement modifier (stat contribution) to its DTO, deriving the effective quality band
   * from the segments so the slider spans the full covered range (see {@link
   * #effectiveQualityMin(BlueprintRequirementModifier)}).
   *
   * @param modifier the modifier entity
   * @return the modifier DTO
   */
  @Mapping(target = "effectiveQualityMin", expression = "java(effectiveQualityMin(modifier))")
  @Mapping(target = "effectiveQualityMax", expression = "java(effectiveQualityMax(modifier))")
  BlueprintRequirementModifierDto toModifierDto(BlueprintRequirementModifier modifier);

  /**
   * Computes the lowest ingredient quality the modifier actually covers. For a stepped modifier the
   * SC Wiki fills the raw {@code qualityMin} with only the first segment's bound, so the smallest
   * segment {@code qualityMin} is used; for the simple linear form (no segments) the raw {@code
   * qualityMin} is returned unchanged.
   *
   * @param modifier the modifier entity
   * @return the effective lower quality bound, or {@code null} when neither segments nor the raw
   *     bound provide one
   */
  default Double effectiveQualityMin(BlueprintRequirementModifier modifier) {
    List<BlueprintModifierSegment> segments = modifier.getSegments();
    if (segments == null || segments.isEmpty()) {
      return modifier.getQualityMin();
    }
    return segments.stream()
        .map(BlueprintModifierSegment::getQualityMin)
        .filter(Objects::nonNull)
        .min(Double::compareTo)
        .orElseGet(modifier::getQualityMin);
  }

  /**
   * Computes the highest ingredient quality the modifier actually covers — the largest segment
   * {@code qualityMax} when stepped, else the raw {@code qualityMax}. Mirror of {@link
   * #effectiveQualityMin(BlueprintRequirementModifier)} for the upper bound; together they let the
   * UI span the full {@code 0..1000} band a multi-segment curve covers even though the raw pair
   * only reflects the first segment.
   *
   * @param modifier the modifier entity
   * @return the effective upper quality bound, or {@code null} when neither segments nor the raw
   *     bound provide one
   */
  default Double effectiveQualityMax(BlueprintRequirementModifier modifier) {
    List<BlueprintModifierSegment> segments = modifier.getSegments();
    if (segments == null || segments.isEmpty()) {
      return modifier.getQualityMax();
    }
    return segments.stream()
        .map(BlueprintModifierSegment::getQualityMax)
        .filter(Objects::nonNull)
        .max(Double::compareTo)
        .orElseGet(modifier::getQualityMax);
  }

  /**
   * Maps one modifier curve segment to its DTO (field names match, so the mapping is implicit).
   *
   * @param segment the segment entity
   * @return the segment DTO
   */
  BlueprintRequirementModifierSegmentDto toSegmentDto(BlueprintModifierSegment segment);

  /**
   * Maps an ingredient line to its DTO, taking the display name from the Wiki snapshot and the kind
   * from the enum name.
   *
   * @param ingredient the ingredient entity
   * @return the ingredient DTO
   */
  @Mapping(target = "name", source = "wikiNameSnapshot")
  @Mapping(
      target = "kind",
      expression = "java(ingredient.getKind() == null ? null : ingredient.getKind().name())")
  BlueprintRequirementIngredientDto toIngredientDto(BlueprintIngredient ingredient);

  /**
   * Maps a summary property (aggregated affected stat) to its DTO.
   *
   * @param summary the summary-property entity
   * @return the summary-property DTO
   */
  BlueprintSummaryPropertyDto toSummaryDto(BlueprintSummaryProperty summary);

  /**
   * Maps a dismantle-return line to its DTO, taking the display name from the Wiki snapshot.
   *
   * @param dismantleReturn the dismantle-return entity
   * @return the dismantle-return DTO
   */
  @Mapping(target = "name", source = "wikiNameSnapshot")
  BlueprintDismantleReturnDto toDismantleReturnDto(BlueprintDismantleReturn dismantleReturn);
}
