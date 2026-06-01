package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Create-or-update payload for a material claim. The triple {@code (materialId, qualityRequirement,
 * claimingOrgUnitId)} identifies the bucket+squadron — posting it again with a new {@code amount}
 * updates the squadron's existing claim rather than inserting a duplicate (one-claim-per-bucket
 * invariant). To withdraw, the caller uses {@code DELETE /claims/{claimId}} instead of posting a
 * zero amount.
 *
 * @param materialId the material being claimed; must reference a bucket that exists on the order
 * @param qualityRequirement the quality bucket ({@code GOOD} or {@code NONE})
 * @param claimingOrgUnitId the squadron making the claim (must be one the caller may act for)
 * @param amount the claimed partial quantity; strictly positive, and bounded by the bucket's open
 *     remaining (no overclaim) at the service layer
 */
public record CreateClaimDto(
    @NotNull UUID materialId,
    @NotNull QualityRequirement qualityRequirement,
    @NotNull UUID claimingOrgUnitId,
    @NotNull @Positive Double amount) {}
