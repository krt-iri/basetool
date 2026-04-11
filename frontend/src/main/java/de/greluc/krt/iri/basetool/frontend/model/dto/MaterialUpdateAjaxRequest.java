package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MaterialUpdateAjaxRequest(
    @NotBlank String updateType,
    UUID categoryId,
    UUID refinedMaterialId,
    String quantityType,
    @NotNull Long version
) {}
