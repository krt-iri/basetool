package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ShipTypeForm(
        @NotBlank(message = "{validation.name.required}")
        @Size(max = 255)
        String name,

        @NotNull(message = "{validation.manufacturer.required}")
        UUID manufacturerId,

        @Size(max = 2000)
        String description
) {
}
