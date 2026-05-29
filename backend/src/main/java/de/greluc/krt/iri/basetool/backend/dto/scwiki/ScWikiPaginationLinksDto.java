package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Pagination links sub-object returned by every paginated SC Wiki endpoint. The client doesn't
 * follow these URIs (it builds its own pages from {@link ScWikiMetaDto#lastPage()}); the DTO exists
 * so Jackson can bind the envelope without choking on the unknown sibling field and so the shape is
 * documented next to {@link ScWikiResponseDto}.
 *
 * @param first URL of page 1 (always present)
 * @param last URL of the last page
 * @param prev URL of the previous page, or {@code null} on page 1
 * @param next URL of the next page, or {@code null} on the last page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiPaginationLinksDto(String first, String last, String prev, String next) {}
