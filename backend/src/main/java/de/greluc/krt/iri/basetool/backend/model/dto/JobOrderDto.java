package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Job Order payload. */
public record JobOrderDto(
    UUID id,
    Integer displayId,
    SquadronReferenceDto creatingSquadron,
    SquadronReferenceDto requestingSquadron,
    String handle,
    String comment,
    Integer priority,
    JobOrderStatus status,
    List<JobOrderMaterialDto> materials,
    List<UserDto> assignees,
    List<JobOrderHandoverDto> handovers,
    Instant createdAt,
    Long version) {}
