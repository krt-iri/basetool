package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form-binding object for Squadron input. */
public record SquadronForm(
    @NotBlank(message = "{validation.name.required}") @Size(max = 255) String name,
    @NotBlank(message = "{validation.shorthand.required}") @Size(max = 50) String shorthand,
    @Size(max = 1000) String description,
    Long version) {}
