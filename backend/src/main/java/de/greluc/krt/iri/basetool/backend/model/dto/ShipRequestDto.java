package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Data transfer record carrying Ship Request payload. */
public record ShipRequestDto(
    String name,
    @NotNull(message = "{validation.shiptype.required}") UUID shipTypeId,
    @NotBlank(message = "{validation.insurance.required}")
        @Pattern(
            regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$",
            message = "{validation.insurance.pattern}")
        String insurance,
    UUID locationId,
    boolean fitted,
    Long version) {}
