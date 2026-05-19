package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between User entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {SquadronMapper.class})
public interface UserMapper {

  /**
   * Maps a {@link User} entity to its full outbound DTO. {@code roles} and {@code permissions} are
   * flattened to plain name sets through the helper methods below.
   */
  @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
  @Mapping(target = "permissions", expression = "java(permissions(user.getRoles()))")
  @Mapping(target = "isLogistician", source = "logistician")
  @Mapping(target = "isMissionManager", source = "missionManager")
  UserDto toDto(User user);

  /** Narrow reference DTO (id + display name) used wherever the full user payload is overkill. */
  UserReferenceDto toReferenceDto(User user);

  /** MapStruct default - flattens the user's roles to a set of role-name strings. */
  default Set<String> roleNames(Set<Role> roles) {
    if (roles == null) {
      return Collections.emptySet();
    }
    return roles.stream().map(Role::getName).collect(Collectors.toSet());
  }

  /** MapStruct default - flattens the permissions of every role the user owns into one set. */
  default Set<String> permissions(Set<Role> roles) {
    if (roles == null) {
      return Collections.emptySet();
    }
    return roles.stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
  }
}
