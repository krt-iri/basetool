package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Data transfer record carrying Material payload. */
public record MaterialDto(
    UUID id,
    String name,
    String type,
    String quantityType,
    String description,
    MaterialDto refinedMaterial,
    MaterialCategoryDto category,
    @JsonProperty("isIllegal") Boolean isIllegal,
    @JsonProperty("isVolatileQt") Boolean isVolatileQt,
    @JsonProperty("isVolatileTime") Boolean isVolatileTime,
    @JsonProperty("isManualRawMaterial") Boolean isManualRawMaterial,
    @JsonProperty("isJobOrder") Boolean isJobOrder,
    @JsonProperty("isManualEntry") Boolean isManualEntry,
    Long version) {}
