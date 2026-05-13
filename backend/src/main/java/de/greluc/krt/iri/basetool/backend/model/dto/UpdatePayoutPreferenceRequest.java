package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import jakarta.validation.constraints.NotNull;

public record UpdatePayoutPreferenceRequest(
    @NotNull @JsonProperty("preference") PayoutPreference preference) {}
