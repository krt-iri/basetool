package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AddUnitRequest(
    @NotBlank(message = "{validation.mission.unit.name.required}") String name,
    UUID shipTypeId,
    UUID shipId,
    Boolean highValueUnit,
    Double frequency) {
  public boolean isHighValueUnit() {
    return highValueUnit != null && highValueUnit;
  }
}
