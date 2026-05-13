package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RefineryOrderStoreDto(@NotEmpty @Valid List<RefineryOrderStoreItemDto> items) {}
