package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code ClaimBucketDto}: the claim view of one aggregated material
 * bucket on a public SK order (required vs. claimed vs. open-remaining plus the individual claims).
 * {@code qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string, matching {@link
 * AggregatedMaterialDto}. No interactive UI consumes this yet (Phase 6).
 *
 * @param material the bucket's material (carries {@code quantityType} for unit-aware display)
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param requiredAmount total amount the order needs for this bucket
 * @param claimedAmount total already claimed across all squadrons
 * @param openRemaining {@code requiredAmount − claimedAmount}, floored at 0
 * @param claims the individual per-squadron claims on this bucket
 */
public record ClaimBucketDto(
    MaterialDto material,
    String qualityRequirement,
    Double requiredAmount,
    Double claimedAmount,
    Double openRemaining,
    List<ClaimDto> claims) {}
