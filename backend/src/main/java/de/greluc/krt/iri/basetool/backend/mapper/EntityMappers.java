package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hand-written {@code Entity -> DTO} converters for the cases where MapStruct cannot be used
 * because the mapping crosses several aggregates or needs custom flattening (e.g. role names plus
 * permission strings rolled up into a single set on {@link UserDto}).
 */
public final class EntityMappers {

  private EntityMappers() {}

  /**
   * Flattens a {@link User} entity into its full DTO, deriving the role-name set and the union of
   * every role's permission strings.
   */
  public static UserDto toDto(User u) {
    Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    Set<String> permissions =
        u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
    de.greluc.krt.iri.basetool.backend.model.dto.SquadronReferenceDto squadronRef =
        u.getSquadron() == null
            ? null
            : new de.greluc.krt.iri.basetool.backend.model.dto.SquadronReferenceDto(
                u.getSquadron().getId(), u.getSquadron().getName(), u.getSquadron().getShorthand());
    return new UserDto(
        u.getId(),
        u.getUsername(),
        u.getDisplayName(),
        u.getEffectiveName(),
        u.getFirstName(),
        u.getLastName(),
        u.getEmail(),
        u.getRank(),
        u.getDescription(),
        roleNames,
        permissions,
        u.getLastReadAnnouncementId(),
        u.isLogistician(),
        u.isMissionManager(),
        u.isInKeycloak(),
        squadronRef,
        u.getVersion(),
        u.getJoinDate());
  }

  /** Flattens a {@link JobType} entity into its DTO, surfacing the {@code parent.id} as a UUID. */
  public static JobTypeDto toDto(JobType jt) {
    UUID parentId = jt.getParent() != null ? jt.getParent().getId() : null;
    return new JobTypeDto(
        jt.getId(),
        jt.getName(),
        jt.getDescription(),
        jt.getArchetype(),
        parentId,
        jt.isActive(),
        jt.isLeadershipRole(),
        jt.getVersion());
  }

  /** Maps a {@link Squadron} entity to its outbound DTO. */
  public static SquadronDto toDto(Squadron s) {
    return new SquadronDto(
        s.getId(), s.getName(), s.getShorthand(), s.getDescription(), s.isActive(), s.getVersion());
  }

  /**
   * Builds a {@link JobType} entity from the DTO. A non-null {@code parentId} is materialised as a
   * stub parent (id only) - the persistence provider resolves the managed instance on persist.
   */
  public static JobType toEntity(JobTypeDto dto) {
    JobType jt = new JobType();
    jt.setId(dto.id());
    jt.setName(dto.name());
    jt.setDescription(dto.description());
    jt.setArchetype(dto.archetype());
    jt.setActive(dto.active());
    jt.setLeadershipRole(dto.isLeadershipRole());
    if (dto.parentId() != null) {
      JobType parent = new JobType();
      parent.setId(dto.parentId());
      jt.setParent(parent);
    } else {
      jt.setParent(null);
    }
    return jt;
  }

  /**
   * Builds a {@link Squadron} entity from the DTO. {@code active} and {@code version} are owned by
   * the service.
   */
  public static Squadron toEntity(SquadronDto dto) {
    Squadron s = new Squadron();
    s.setId(dto.id());
    s.setName(dto.name());
    s.setShorthand(dto.shorthand());
    s.setDescription(dto.description());
    return s;
  }
}
