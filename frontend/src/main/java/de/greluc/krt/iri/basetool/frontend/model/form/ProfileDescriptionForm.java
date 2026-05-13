package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Size;

/** Form-binding object for Profile Description input. */
public record ProfileDescriptionForm(
    @Size(max = 2000, message = "{validation.description.max}") String description,
    @Size(max = 255, message = "{validation.displayname.max}") String displayName,
    Long version) {}
