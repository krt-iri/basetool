package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bounding-box dimensions ({@code {x,y,z}}) nested inside a {@link ScWikiItemDto}
 * (SC_WIKI_SYNC_PLAN.md §3.3). Any axis may be {@code null} when the Wiki omits it.
 *
 * @param x width in metres
 * @param y height in metres
 * @param z depth / length in metres
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiDimensionDto(Double x, Double y, Double z) {}
