package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for parsing a single entry from a HangarXPLOR Shiplist JSON export (see {@code
 * https://github.com/dolkensp/HangarXPLOR}). The shiplist payload has roughly a dozen pledge
 * metadata fields (pledge id, price, date, manufacturer codes, warbond flag …) that the import flow
 * does not need; only the four fields declared below are consumed.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps Jackson tolerant towards
 * forward-compatible additions on the HangarXPLOR side so a new field in a future plugin release
 * does not break our parse step.
 *
 * <p>Format example (single entry):
 *
 * <pre>
 * {
 *   "ship_code":        "ORIG_600i",
 *   "ship_name":        "KRT Olymp",
 *   "manufacturer_code":"ORIG",
 *   "manufacturer_name":"Origin Jumpworks",
 *   "lti":              true,
 *   "name":             "600i Explorer",
 *   "warbond":          false,
 *   "entity_type":      "ship",
 *   "pledge_id":        "29528209",
 *   "pledge_name":      "Standalone Ship - Aurora ES",
 *   "pledge_date":      "May 10, 2021",
 *   "pledge_cost":      "$250.00 USD"
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiplistEntryDto(
    @JsonProperty("name") String name,
    @JsonProperty("ship_name") String shipName,
    @JsonProperty("entity_type") String entityType,
    @JsonProperty("lti") Boolean lti) {}
