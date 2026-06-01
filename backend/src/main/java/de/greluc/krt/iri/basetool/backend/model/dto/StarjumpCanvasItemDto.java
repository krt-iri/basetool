package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for a single {@code canvasItems} element of a StarJump FleetViewer JSON export (the
 * "Hangar Link" download from {@code https://fleetviewer.link}). A FleetViewer canvas mixes ship
 * tiles ({@code "itemType":"SHIP"}) with purely decorative text labels ({@code
 * "itemType":"TEXTGROUP"}) and other widgets; only {@code SHIP} items carry hangar content. Every
 * field below is the subset the import flow needs — the dozens of layout/styling fields (positions,
 * zoom, gradients, fonts …) are ignored.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps Jackson tolerant towards the many
 * unconsumed layout fields and any forward-compatible additions in a future FleetViewer release.
 *
 * <p>Format example (single SHIP item, layout fields elided):
 *
 * <pre>
 * {
 *   "itemType":    "SHIP",
 *   "shipSlug":    "f7c-m-super-hornet-mk-ii",
 *   "variantSlug": "",
 *   "defaultText": "F7C-M Super Hornet Mk II"
 * }
 * </pre>
 *
 * @param itemType the canvas-item discriminator; only {@code "SHIP"} (case-insensitive) is imported
 * @param shipSlug FleetViewer's kebab-case ship slug (e.g. {@code "f7c-m-super-hornet-mk-ii"}),
 *     used as the slug-fallback match key against {@code ShipType.uexSlug} / {@code
 *     ShipType.scwikiSlug}
 * @param variantSlug FleetViewer's variant slug; empty in current exports and currently unused
 * @param defaultText the human-readable ship name (e.g. {@code "F7C-M Super Hornet Mk II"}), the
 *     primary key fed into the name matcher
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarjumpCanvasItemDto(
    @JsonProperty("itemType") String itemType,
    @JsonProperty("shipSlug") String shipSlug,
    @JsonProperty("variantSlug") String variantSlug,
    @JsonProperty("defaultText") String defaultText) {}
