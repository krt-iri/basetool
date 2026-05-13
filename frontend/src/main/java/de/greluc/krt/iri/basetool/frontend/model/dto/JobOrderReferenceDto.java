package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

public record JobOrderReferenceDto(
    UUID id,
    Integer displayId,
    String squadron,
    String handle,
    String status,
    List<JobOrderMaterialDto> materials) {}
