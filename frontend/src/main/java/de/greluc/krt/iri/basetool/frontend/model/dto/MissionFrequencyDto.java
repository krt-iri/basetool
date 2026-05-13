package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MissionFrequencyDto(
    UUID id, FrequencyTypeRef frequencyType, BigDecimal value, Long version) {
  public record FrequencyTypeRef(UUID id, String name) {}

  public UUID frequencyTypeId() {
    return frequencyType != null ? frequencyType.id() : null;
  }
}
