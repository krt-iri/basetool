package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Generic envelope returned by every paginated SC Wiki endpoint. The type parameter {@code T} is
 * one of the {@code ScWiki*Dto} records in this package — bound at the {@link
 * de.greluc.krt.iri.basetool.backend.integration.scwiki.ScWikiClient} call site to a concrete row
 * type (e.g. {@link ScWikiCommodityDto} in R3).
 *
 * <p>Distinct from the UEX envelope in two ways: there is no {@code status} field, and the response
 * carries a {@code meta} + {@code links} block that drives pagination. Unknown fields are tolerated
 * (the Wiki occasionally adds debug fields on errors).
 *
 * <p>Following the same Javadoc-tag exception as {@code UexResponseDto}: a {@code @param <T>} tag
 * is intentionally omitted; Checkstyle's {@code MissingJavadocMethod} machinery applies the
 * record-level {@code @param} tags to the synthetic record accessors, which is meaningless for a
 * type parameter and surfaces as spurious "@param tag does not match any actual parameter"
 * findings.
 *
 * @param data the payload rows for the current page
 * @param meta pagination metadata
 * @param links pagination link URIs (informational; the client doesn't follow them)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiResponseDto<T>(
    List<T> data, ScWikiMetaDto meta, ScWikiPaginationLinksDto links) {}
