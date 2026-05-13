package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;

/** Form-binding object for Star System input. */
public record StarSystemForm(
    @NotBlank(message = "{validation.name.required}") String name, String description) {}
