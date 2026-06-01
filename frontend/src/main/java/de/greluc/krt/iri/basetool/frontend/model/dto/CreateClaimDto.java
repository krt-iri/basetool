package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code CreateClaimDto}: the create-or-update payload for a
 * material claim. The triple {@code (materialId, qualityRequirement, claimingOrgUnitId)} identifies
 * the bucket+squadron; re-posting with a new {@code amount} updates the existing claim. {@code
 * qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string. No interactive UI produces
 * this yet (Phase 6).
 *
 * @param materialId the material being claimed
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param claimingOrgUnitId the squadron making the claim
 * @param amount the claimed partial quantity (strictly positive)
 */
public record CreateClaimDto(
    UUID materialId, String qualityRequirement, UUID claimingOrgUnitId, Double amount) {}
