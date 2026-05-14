package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Create Job Order Material payload. */
public record CreateJobOrderMaterialDto(UUID materialId, Integer minQuality, Double amount) {}
