package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionCategoryUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Entity ↔ DTO mapper for {@link PromotionCategory}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PromotionCategoryMapper {

  /**
   * Converts a {@link PromotionCategory} into its {@link PromotionCategoryResponse} DTO, flattening
   * the parent topic's id and name so the client can render the category in context without an
   * extra fetch.
   *
   * @param entity the managed category to convert
   * @return the response DTO mirroring the entity's fields
   */
  @Mapping(target = "topicId", source = "topic.id")
  @Mapping(target = "topicName", source = "topic.name")
  PromotionCategoryResponse toResponse(PromotionCategory entity);

  /**
   * Builds a new {@link PromotionCategory} entity from a creation request. The {@code topic}
   * association and {@code levelContents} collection are left unset and must be wired by the
   * service layer.
   *
   * @param request validated payload describing the new category
   * @return a transient entity ready to be persisted
   */
  @Mapping(target = "topic", ignore = true)
  @Mapping(target = "levelContents", ignore = true)
  PromotionCategory toEntity(PromotionCategoryCreateRequest request);

  /**
   * Copies the writable fields from a {@link PromotionCategoryUpdateRequest} onto an existing
   * managed {@link PromotionCategory}. Identity, audit, and association fields are deliberately
   * ignored to preserve them across the update.
   *
   * @param entity the managed category to mutate in place
   * @param request validated payload carrying the new field values
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "topic", ignore = true)
  @Mapping(target = "levelContents", ignore = true)
  void updateEntity(
      @MappingTarget PromotionCategory entity, PromotionCategoryUpdateRequest request);
}
