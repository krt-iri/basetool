package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Job Order payload. */
public record JobOrderDto(
    UUID id,
    Integer displayId,
    String squadron,
    SquadronReferenceDto creatingSquadron,
    SquadronReferenceDto requestingSquadron,
    String handle,
    Integer priority,
    String status,
    List<JobOrderMaterialDto> materials,
    List<UserDto> assignees,
    List<JobOrderHandoverDto> handovers,
    Instant createdAt,
    Long version) {}
