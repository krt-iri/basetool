package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import java.util.List;
import java.util.UUID;

public record JobOrderReferenceDto(
    UUID id,
    Integer displayId,
    String squadron,
    String handle,
    JobOrderStatus status,
    List<JobOrderMaterialDto> materials) {}
