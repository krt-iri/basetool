package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.Set;
import java.util.UUID;

/** Inbound request payload for the Update Crew operation. */
public record UpdateCrewRequest(Set<UUID> jobTypeIds) {}
