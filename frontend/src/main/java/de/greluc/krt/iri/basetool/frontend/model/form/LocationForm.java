package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;

public record LocationForm(
    @NotBlank(message = "{validation.name.required}") String name,
    String description,
    Long version) {}
