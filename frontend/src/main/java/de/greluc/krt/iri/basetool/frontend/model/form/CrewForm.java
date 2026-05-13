package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.List;
import java.util.UUID;

/** Form-binding object for Crew input. */
public record CrewForm(UUID participantId, List<UUID> jobTypeIds) {}
