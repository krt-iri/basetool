package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Inbound request payload for the Add Participant Public operation. */
public record AddParticipantPublicRequest(
    UUID userId, String guestName, UUID desiredJobTypeId, String comment, UUID squadronId) {}
