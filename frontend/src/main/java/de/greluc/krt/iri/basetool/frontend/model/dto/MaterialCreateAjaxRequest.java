package de.greluc.krt.iri.basetool.frontend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound payload for the admin {@code POST /admin/materials/ajax} create handler. Mirrors the
 * backend {@code MaterialCreateDto} field-for-field so Jackson serialisation across the WebClient
 * relay stays straight; {@code isManualEntry} is intentionally absent — the backend stamps it
 * server-side on creation.
 *
 * @param name unique material name; matched by the UEX sync's {@code findByName} fallback when UEX
 *     later picks the commodity up.
 * @param type material classification ({@code RAW}, {@code REFINED}, {@code NO_REFINE}).
 * @param quantityType inventory quantity unit ({@code SCU} or {@code PIECE}).
 * @param description optional free-text note shown on the admin list.
 * @param refinedMaterialId optional FK; only honoured when {@code type=RAW} or {@code
 *     isManualRawMaterial=true}.
 * @param categoryId optional FK to {@code MaterialCategory}.
 * @param isManualRawMaterial UEX-classification override that makes the material selectable as a
 *     refinery input.
 * @param isJobOrder marks the material for the job-order picker.
 * @param isIllegal warning flag (illegal cargo).
 * @param isVolatileQt warning flag (volatile under Quantum Travel).
 * @param isVolatileTime warning flag (decays over time).
 */
public record MaterialCreateAjaxRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String type,
    @NotBlank String quantityType,
    @Size(max = 4000) String description,
    UUID refinedMaterialId,
    UUID categoryId,
    @JsonProperty("isManualRawMaterial") Boolean isManualRawMaterial,
    @JsonProperty("isJobOrder") Boolean isJobOrder,
    @JsonProperty("isIllegal") Boolean isIllegal,
    @JsonProperty("isVolatileQt") Boolean isVolatileQt,
    @JsonProperty("isVolatileTime") Boolean isVolatileTime) {}
