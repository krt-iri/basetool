package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;

public record UpdatePayoutPreferenceRequest(
        @NotNull
        @JsonProperty("preference")
        PayoutPreference preference
) {
}
