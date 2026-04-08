package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
    @Mapping(target = "permissions", expression = "java(permissions(user.getRoles()))")
    @Mapping(target = "isLogistician", source = "logistician")
    @Mapping(target = "isMissionManager", source = "missionManager")
    UserDto toDto(User user);

    UserReferenceDto toReferenceDto(User user);

    // --- helper mappers ---
    default Set<String> roleNames(Set<Role> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    default Set<String> permissions(Set<Role> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
    }
}
