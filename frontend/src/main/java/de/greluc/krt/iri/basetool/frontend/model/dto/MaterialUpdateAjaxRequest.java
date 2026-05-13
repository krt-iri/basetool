package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Inbound request payload for the Material Update Ajax operation. */
public record MaterialUpdateAjaxRequest(
    @NotBlank String updateType,
    UUID categoryId,
    UUID refinedMaterialId,
    String quantityType,
    Boolean isManualRawMaterial,
    Boolean isJobOrder,
    @NotNull Long version) {}
