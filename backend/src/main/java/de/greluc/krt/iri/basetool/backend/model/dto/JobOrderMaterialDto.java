package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Job Order Material payload. */
public record JobOrderMaterialDto(
    UUID id,
    MaterialDto material,
    Integer minQuality,
    Double amount,
    Double currentStock,
    Long version) {}
