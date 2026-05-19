package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Job Order Reference payload. */
public record JobOrderReferenceDto(
    UUID id,
    Integer displayId,
    String handle,
    String status,
    List<JobOrderMaterialDto> materials) {}
