package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

public record CreateJobOrderDto(
        String squadron,
        String handle,
        List<CreateJobOrderMaterialDto> materials,
        Long version
) {}
