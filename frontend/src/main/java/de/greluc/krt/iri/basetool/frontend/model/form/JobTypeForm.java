package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form-binding object for Job Type input. */
public record JobTypeForm(
    @NotBlank(message = "{validation.name.required}") @Size(max = 255) String name,
    @Size(max = 1000) String description,
    @NotBlank(message = "{validation.archetype.required}") @Size(max = 50) String archetype,
    Boolean isLeadershipRole,
    Long version) {
  public Boolean getIsLeadershipRole() {
    return isLeadershipRole;
  }
}
