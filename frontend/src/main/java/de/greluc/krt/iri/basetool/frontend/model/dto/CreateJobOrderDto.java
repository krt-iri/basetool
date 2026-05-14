package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/** Data transfer record carrying Create Job Order payload. */
public record CreateJobOrderDto(
    String squadron, String handle, List<CreateJobOrderMaterialDto> materials, Long version) {}
