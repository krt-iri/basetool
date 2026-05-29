package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Manufacturer reference ({@code {uuid,name,code}}) nested inside a {@link ScWikiItemDto}
 * (SC_WIKI_SYNC_PLAN.md §3.3). R4 captures it but does NOT write it onto {@code game_item} — the
 * manufacturer link is sticky on the UEX value (§6.3.5); the dedicated manufacturer reconciliation
 * is R6. The DTO exists so the payload binds cleanly and R6 has the data ready.
 *
 * @param uuid Wiki manufacturer UUID
 * @param name manufacturer display name
 * @param code manufacturer short code (e.g. {@code "RSI"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiItemManufacturerDto(UUID uuid, String name, String code) {}
