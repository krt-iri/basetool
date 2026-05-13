package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Inbound request payload for the Add Participant operation. */
public record AddParticipantRequest(@NotNull UUID userId) {}
