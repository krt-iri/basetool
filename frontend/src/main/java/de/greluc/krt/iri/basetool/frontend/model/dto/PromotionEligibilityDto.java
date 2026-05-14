package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend's {@code PromotionEligibilityResponse}.
 *
 * @param userId the evaluated member's JWT-sub identifier
 * @param fromRank the rank the member currently holds
 * @param toRank the rank the member would be promoted to
 * @param eligible {@code true} iff every entry in {@code checks} is satisfied
 * @param hasConfiguredRules {@code true} iff at least one requirement is configured for this
 *     transition
 * @param checks per-requirement evaluation, in stable display order
 */
public record PromotionEligibilityDto(
    String userId,
    int fromRank,
    int toRank,
    boolean eligible,
    boolean hasConfiguredRules,
    List<PromotionRequirementCheckDto> checks) {}
