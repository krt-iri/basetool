package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Inbound request payload for the Add Participant Public operation. {@code @Size} caps cap the
 * anonymous attack surface — without them an unauthenticated caller could spam multi-megabyte
 * {@code guestName} / {@code comment} payloads through the public participant-add endpoint until
 * the {@code mission_participant} table is full (audit finding H-2).
 *
 * <p>{@code orgUnitIds} is honoured only for GUEST entries (and only when the authenticated caller
 * may label those org units — see {@code MissionService.resolveGuestSubmittedOrgUnits}); for a
 * registered participant the affiliations are auto-derived server-side from the user's memberships
 * and any submitted list is ignored.
 */
public record AddParticipantPublicRequest(
    UUID userId,
    @Size(max = 100) String guestName,
    UUID desiredJobTypeId,
    @Size(max = 1000) String comment,
    List<UUID> orgUnitIds) {}
