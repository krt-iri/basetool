package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UnitForm(
    @NotBlank(message = "{validation.name.required}") @Size(max=255) String name,
    UUID shipTypeId,
    UUID shipId,
    boolean highValueUnit,
    Double frequency
) {}
