package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record CreateJobOrderMaterialDto(UUID materialId, Integer minQuality, Double amount) {}
