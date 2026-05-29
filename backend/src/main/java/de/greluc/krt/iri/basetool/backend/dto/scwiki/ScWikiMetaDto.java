package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination metadata returned by every paginated SC Wiki endpoint. Lives inside the {@code meta}
 * field of {@link ScWikiResponseDto}; consumed by {@code ScWikiClient.fetchAllPages} to decide when
 * to stop walking pages.
 *
 * <p>The standard envelope shape (verified against the live API on 2026-05-27) is:
 *
 * <pre>{@code
 * {
 *   "data": [ ... ],
 *   "links": { ... },
 *   "meta": {
 *     "current_page": 1,
 *     "last_page":    2,
 *     "per_page":    200,
 *     "total":       205
 *   }
 * }
 * }</pre>
 *
 * <p>Some sub-resource endpoints (e.g. {@code /api/blueprints/{uuid}}) return a single row without
 * an envelope; this DTO is not used for those.
 *
 * @param currentPage 1-based page number this response represents
 * @param lastPage highest page number for the current filter / sort
 * @param perPage rows per page (= the {@code ?page[size]=…} we sent)
 * @param total total row count across all pages
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiMetaDto(
    @JsonProperty("current_page") Integer currentPage,
    @JsonProperty("last_page") Integer lastPage,
    @JsonProperty("per_page") Integer perPage,
    @JsonProperty("total") Integer total) {}
