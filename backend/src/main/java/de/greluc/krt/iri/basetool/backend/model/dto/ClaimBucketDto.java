package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import java.util.List;

/**
 * The claim view of one aggregated material bucket on a public SK order: how much the order
 * requires for this material+quality, how much squadrons have collectively claimed, the open
 * remainder, and the individual claims. Drives the read-only claim surface (Phase 5) and the
 * interactive claim UI (Phase 6).
 *
 * @param material the bucket's material (with {@code quantityType} for unit-aware formatting)
 * @param qualityRequirement the quality bucket ({@code GOOD} or {@code NONE})
 * @param requiredAmount total amount the order needs for this bucket (Σ over material lines /
 *     item-derived requirements)
 * @param claimedAmount total already claimed across all squadrons (Σ of {@code claims.amount})
 * @param openRemaining {@code requiredAmount − claimedAmount}, floored at 0
 * @param claims the individual per-squadron claims on this bucket
 */
public record ClaimBucketDto(
    MaterialDto material,
    QualityRequirement qualityRequirement,
    Double requiredAmount,
    Double claimedAmount,
    Double openRemaining,
    List<ClaimDto> claims) {}
