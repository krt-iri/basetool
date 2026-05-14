package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Inbound request payload for the Add Crew operation. */
public record AddCrewRequest(@NotNull UUID participantId, Set<UUID> jobTypeIds) {}
