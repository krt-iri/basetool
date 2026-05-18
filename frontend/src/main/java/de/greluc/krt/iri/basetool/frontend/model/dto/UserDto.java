package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Data transfer record carrying User payload. */
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
    @Nullable SquadronReferenceDto squadron,
    Long version,
    @Nullable LocalDate joinDate) {}
