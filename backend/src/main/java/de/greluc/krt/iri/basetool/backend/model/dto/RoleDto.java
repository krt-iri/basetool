package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.Set;

/** Data transfer record carrying Role payload. */
public record RoleDto(
    Long id, String name, String description, Set<String> permissions, Long version) {}
