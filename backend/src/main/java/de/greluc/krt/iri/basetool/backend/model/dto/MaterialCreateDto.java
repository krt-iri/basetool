package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound DTO for the admin {@code POST /api/v1/materials} create flow. UEX-imported fields ({@code
 * idCommodity}, {@code code}, {@code slug}, {@code priceBuy}, …) are intentionally absent — they
 * stay {@code null} for manual entries and get populated by the next UEX sync if the commodity
 * later appears upstream. The server stamps {@code isManualEntry=true} on creation; the client
 * cannot set it via this payload.
 *
 * @param name unique material name; matched against UEX commodity names by the sync's {@code
 *     findByName} fallback when UEX later picks the material up.
 * @param type material classification ({@code RAW}, {@code REFINED}, {@code NO_REFINE}).
 * @param quantityType inventory quantity unit ({@code SCU} or {@code PIECE}).
 * @param description optional free-text note (e.g. "manually created — missing from UEX").
 * @param refinedMaterialId optional FK to the refined output material; only honoured when {@code
 *     type=RAW} or {@code isManualRawMaterial=true}, rejected otherwise.
 * @param categoryId optional FK to {@code MaterialCategory}.
 * @param isManualRawMaterial UEX-classification override that makes the material selectable as
 *     refinery input even when UEX would classify it as {@code NO_REFINE}/{@code REFINED}.
 * @param isJobOrder marks the material as a job-order picker entry.
 * @param isIllegal warning flag (illegal cargo).
 * @param isVolatileQt warning flag (volatile under Quantum Travel).
 * @param isVolatileTime warning flag (decays over time).
 */
public record MaterialCreateDto(
    @NotBlank @Size(max = 255) String name,
    @NotNull String type,
    @NotNull String quantityType,
    @Size(max = 4000) String description,
    UUID refinedMaterialId,
    UUID categoryId,
    @JsonProperty("isManualRawMaterial") Boolean isManualRawMaterial,
    @JsonProperty("isJobOrder") Boolean isJobOrder,
    @JsonProperty("isIllegal") Boolean isIllegal,
    @JsonProperty("isVolatileQt") Boolean isVolatileQt,
    @JsonProperty("isVolatileTime") Boolean isVolatileTime) {}
