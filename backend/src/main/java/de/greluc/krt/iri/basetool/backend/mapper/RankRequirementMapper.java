package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Entity ↔ DTO mapper for {@link RankRequirement}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RankRequirementMapper {

  /**
   * Converts a {@link RankRequirement} into its {@link RankRequirementResponse} DTO, flattening the
   * linked topic and category into id/name pairs so the client can render the requirement without
   * follow-up fetches.
   *
   * @param entity the managed requirement to convert
   * @return the response DTO mirroring the entity's fields
   */
  @Mapping(target = "topicId", source = "topic.id")
  @Mapping(target = "topicName", source = "topic.name")
  @Mapping(target = "categoryId", source = "category.id")
  @Mapping(target = "categoryName", source = "category.name")
  RankRequirementResponse toResponse(RankRequirement entity);

  /**
   * Builds a new {@link RankRequirement} entity from a creation request. The {@code topic} and
   * {@code category} associations are left unset and must be wired by the service layer.
   *
   * @param request validated payload describing the new requirement
   * @return a transient entity ready to be persisted
   */
  @Mapping(target = "topic", ignore = true)
  @Mapping(target = "category", ignore = true)
  RankRequirement toEntity(RankRequirementCreateRequest request);

  /**
   * Copies the writable fields from a {@link RankRequirementUpdateRequest} onto an existing managed
   * {@link RankRequirement}. Identity, audit, and association fields are deliberately ignored to
   * preserve them across the update.
   *
   * @param entity the managed requirement to mutate in place
   * @param request validated payload carrying the new field values
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "topic", ignore = true)
  @Mapping(target = "category", ignore = true)
  void updateEntity(@MappingTarget RankRequirement entity, RankRequirementUpdateRequest request);
}
