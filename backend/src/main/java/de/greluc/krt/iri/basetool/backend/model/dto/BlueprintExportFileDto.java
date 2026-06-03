package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for parsing the root of an uploaded external blueprint export (#327, Phase 4). Both
 * supported exporters — the SCMDB log-watcher and the <a
 * href="https://github.com/krt-iri/basetool-bp-extractor">Basetool Blueprint Extractor</a> — wrap
 * their records in a top-level {@code blueprints} array; every other top-level field (schema
 * version, tool metadata, mission list, player summaries, …) is ignored.
 *
 * @param blueprints the acquired-blueprint entries; {@code null} if the key is absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintExportFileDto(
    @JsonProperty("blueprints") List<BlueprintExportEntryDto> blueprints) {}
