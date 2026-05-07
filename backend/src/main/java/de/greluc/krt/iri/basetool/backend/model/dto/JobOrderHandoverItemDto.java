package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record JobOrderHandoverItemDto(
        UUID id,
        UUID jobOrderHandoverId,
        MaterialDto material,
        Integer quality,
        Double amount,
        String locationName,
        Long version
) {
}
