package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for the root of a StarJump FleetViewer JSON export (the "Hangar Link" download from
 * {@code https://fleetviewer.link}). Unlike the two array-based formats (CCU Game Fleetview and
 * HangarXPLOR Shiplist), a FleetViewer export is a single JSON <em>object</em> carrying a {@code
 * type} discriminator, a {@code version} and a {@code canvasItems} array that holds the ship tiles
 * alongside decorative widgets.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} discards the many top-level layout fields
 * (background, zoom, pan, render box, icon positions …) the import flow does not need.
 *
 * <p>Format example (top-level shape, canvas items elided):
 *
 * <pre>
 * {
 *   "type":        "starjumpFleetviewer",
 *   "version":     1,
 *   "canvasItems": [ { "itemType": "SHIP", "shipSlug": "perseus", "defaultText": "Perseus" }, ... ]
 * }
 * </pre>
 *
 * @param type the export discriminator (expected {@code "starjumpFleetviewer"}, case-insensitive)
 * @param canvasItems the canvas elements; ship tiles and decorative widgets intermixed, may be
 *     {@code null} on a malformed/empty export
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarjumpFleetviewerDto(
    @JsonProperty("type") String type,
    @JsonProperty("canvasItems") List<StarjumpCanvasItemDto> canvasItems) {}
