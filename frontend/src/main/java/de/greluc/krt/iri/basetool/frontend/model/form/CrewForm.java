package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.List;
import java.util.UUID;

public record CrewForm(
    UUID participantId,
    List<UUID> jobTypeIds
) {}
