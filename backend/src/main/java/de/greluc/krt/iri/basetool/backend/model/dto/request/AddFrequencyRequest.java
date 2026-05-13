package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AddFrequencyRequest(@NotNull UUID frequencyTypeId, @NotNull BigDecimal value) {}
