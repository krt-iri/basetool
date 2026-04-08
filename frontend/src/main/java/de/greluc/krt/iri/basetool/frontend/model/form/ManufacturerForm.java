package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManufacturerForm(
        @NotBlank(message = "{validation.name.required}")
        @Size(max = 255)
        String name,

        @NotBlank(message = "{validation.abbreviation.required}")
        @Size(max = 50)
        String abbreviation,

        @Size(max = 255)
        String nickname,

        @Size(max = 255)
        String wiki,

        @Size(max = 2000)
        String description
) {
}
