package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One squadron's material claim ("Eintragung") on a public SK order, as exposed at the API
 * boundary. The owning bucket (material + quality) is carried by the enclosing {@link
 * ClaimBucketDto}; this record only adds the per-squadron facts.
 *
 * @param id claim primary key
 * @param claimingOrgUnit the squadron that signed up (slim reference for the badge)
 * @param amount the claimed partial quantity in the material's own unit
 * @param claimedByUserId id of the user who last created/updated the claim (audit; never a name, so
 *     no PII is exposed)
 * @param claimedAt creation instant (UTC)
 * @param version optimistic-lock version (echoed back on update/withdraw)
 */
public record ClaimDto(
    UUID id,
    SquadronReferenceDto claimingOrgUnit,
    Double amount,
    UUID claimedByUserId,
    Instant claimedAt,
    Long version) {}
