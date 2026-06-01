package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code ClaimDto}: one squadron's material claim ("Eintragung") on
 * a public SK order. The owning bucket (material + quality) is carried by the enclosing {@link
 * ClaimBucketDto}. No interactive UI consumes this yet (Phase 6) — the mirror exists so the
 * frontend's DTO surface stays symmetric with the backend.
 *
 * @param id claim primary key
 * @param claimingOrgUnit the squadron that signed up (slim reference)
 * @param amount the claimed partial quantity
 * @param claimedByUserId id of the user who last touched the claim (audit; never a name)
 * @param claimedAt creation instant (UTC)
 * @param version optimistic-lock version
 */
public record ClaimDto(
    UUID id,
    SquadronReferenceDto claimingOrgUnit,
    Double amount,
    UUID claimedByUserId,
    Instant claimedAt,
    Long version) {}
