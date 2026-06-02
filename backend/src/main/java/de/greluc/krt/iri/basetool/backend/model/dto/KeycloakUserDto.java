package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import java.util.UUID;

/** Data transfer record carrying Keycloak User payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserDto(
    UUID id, String username, String email, Boolean enabled, Set<String> roles) {}
