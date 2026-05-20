package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound request payload for the Add Participant Public operation. {@code @Size} caps cap the
 * anonymous attack surface — without them an unauthenticated caller could spam multi-megabyte
 * {@code guestName} / {@code comment} payloads through the public participant-add endpoint until
 * the {@code mission_participant} table is full (audit finding H-2).
 */
public record AddParticipantPublicRequest(
    UUID userId,
    @Size(max = 100) String guestName,
    UUID desiredJobTypeId,
    @Size(max = 1000) String comment,
    UUID squadronId) {}
