package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for parsing a single blueprint entry from an SCMDB log-watcher JSON export (#327,
 * Phase 4). The watcher payload carries a number of fields per blueprint (recipe id, category,
 * quantities …) that the import does not need; only the two below are consumed.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the parse tolerant towards new SCMDB
 * fields so a future watcher release does not break the import.
 *
 * @param productName the crafted product's name (matched against the master product list)
 * @param ts acquisition timestamp as fractional Unix epoch seconds, or {@code null} if absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScmdbBlueprintEntryDto(
    @JsonProperty("productName") String productName, @JsonProperty("ts") Double ts) {}
