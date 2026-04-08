package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String displayName,
        String effectiveName,
        String firstName,
        String lastName,
        String email,
        Integer rank,
        String description,
        Set<String> roles,
        Set<String> permissions,
        UUID lastReadAnnouncementId,
        Boolean isLogistician,
        Boolean isMissionManager,
        Boolean inKeycloak,
        Long version
) {}
