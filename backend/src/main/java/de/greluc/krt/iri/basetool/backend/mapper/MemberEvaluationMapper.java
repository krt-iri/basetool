package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.iri.basetool.backend.model.dto.MemberEvaluationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Entity ↔ DTO mapper for {@link MemberEvaluation}. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberEvaluationMapper {

  /**
   * Converts a {@link MemberEvaluation} into its {@link MemberEvaluationResponse} DTO, flattening
   * the linked {@link de.greluc.krt.iri.basetool.backend.model.PromotionCategory} and its parent
   * topic into id/name pairs so the client can group evaluations without a follow-up call.
   *
   * @param entity the managed evaluation to convert
   * @return the response DTO mirroring the entity's fields
   */
  @Mapping(target = "categoryId", source = "category.id")
  @Mapping(target = "categoryName", source = "category.name")
  @Mapping(target = "topicId", source = "category.topic.id")
  @Mapping(target = "topicName", source = "category.topic.name")
  MemberEvaluationResponse toResponse(MemberEvaluation entity);
}
