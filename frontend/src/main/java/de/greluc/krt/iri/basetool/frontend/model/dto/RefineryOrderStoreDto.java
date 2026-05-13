package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/** Data transfer record carrying Refinery Order Store payload. */
public record RefineryOrderStoreDto(List<RefineryOrderStoreItemDto> items) {}
