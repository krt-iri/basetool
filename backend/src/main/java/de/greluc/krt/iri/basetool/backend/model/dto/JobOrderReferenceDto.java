package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Job Order Reference payload. */
public record JobOrderReferenceDto(
    UUID id,
    Integer displayId,
    String handle,
    JobOrderStatus status,
    List<JobOrderMaterialDto> materials) {}
