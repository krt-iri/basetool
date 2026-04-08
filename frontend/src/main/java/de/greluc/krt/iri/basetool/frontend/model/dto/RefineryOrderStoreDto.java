package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

public record RefineryOrderStoreDto(
    List<RefineryOrderStoreItemDto> items
) {}
