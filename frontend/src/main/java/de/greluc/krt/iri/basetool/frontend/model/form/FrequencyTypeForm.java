package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FrequencyTypeForm(
    @NotBlank(message = "{error.validation.not_blank}") @Size(max = 255, message = "{error.validation.size}") String name,
    String description,
    Long version) {}
