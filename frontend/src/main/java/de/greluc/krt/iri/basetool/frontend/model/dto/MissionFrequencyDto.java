package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Frequency payload. */
public record MissionFrequencyDto(
    UUID id, FrequencyTypeRef frequencyType, BigDecimal value, Long version) {
  /** Immutable record carrying Frequency Type Ref data. */
  public record FrequencyTypeRef(UUID id, String name) {}

  /** Convenience accessor returning the nested {@code frequencyType.id()}, or {@code null}. */
  public UUID frequencyTypeId() {
    return frequencyType != null ? frequencyType.id() : null;
  }
}
