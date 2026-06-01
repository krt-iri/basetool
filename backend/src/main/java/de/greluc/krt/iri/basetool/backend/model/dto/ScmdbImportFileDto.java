package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for parsing the root of an SCMDB log-watcher JSON export (#327, Phase 4). The export
 * is an object carrying a {@code blueprints} array; every other top-level field (export metadata,
 * version, etc.) is ignored.
 *
 * @param blueprints the acquired-blueprint entries; {@code null} if the key is absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScmdbImportFileDto(
    @JsonProperty("blueprints") List<ScmdbBlueprintEntryDto> blueprints) {}
