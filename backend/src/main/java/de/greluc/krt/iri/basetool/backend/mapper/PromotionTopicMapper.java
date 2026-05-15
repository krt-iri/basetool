package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Entity ↔ DTO mapper for {@link PromotionTopic}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PromotionTopicMapper {

  /**
   * Converts a {@link PromotionTopic} into its {@link PromotionTopicResponse} DTO without exposing
   * the child categories collection.
   *
   * @param entity the managed topic to convert
   * @return the response DTO mirroring the entity's fields
   */
  PromotionTopicResponse toResponse(PromotionTopic entity);

  /**
   * Builds a new {@link PromotionTopic} entity from a creation request. The {@code categories}
   * collection is left empty and must be populated separately.
   *
   * @param request validated payload describing the new topic
   * @return a transient entity ready to be persisted
   */
  @Mapping(target = "categories", ignore = true)
  PromotionTopic toEntity(PromotionTopicCreateRequest request);

  /**
   * Copies the writable fields from a {@link PromotionTopicUpdateRequest} onto an existing managed
   * {@link PromotionTopic}. Identity, audit, and association fields are deliberately ignored to
   * preserve them across the update.
   *
   * @param entity the managed topic to mutate in place
   * @param request validated payload carrying the new field values
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "categories", ignore = true)
  void updateEntity(@MappingTarget PromotionTopic entity, PromotionTopicUpdateRequest request);
}
