package de.greluc.krt.iri.basetool.backend.model.dto;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
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
        Long version,
        @Nullable LocalDate joinDate
) {}
