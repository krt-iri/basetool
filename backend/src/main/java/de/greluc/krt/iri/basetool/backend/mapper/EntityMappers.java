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

public final class EntityMappers {

    private EntityMappers() {}

    public static UserDto toDto(User u) {
        Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> permissions = u.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toSet());
        return new UserDto(
                u.getId(), u.getUsername(), u.getDisplayName(), u.getEffectiveName(), u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getRank(), u.getDescription(), roleNames, permissions, u.getLastReadAnnouncementId(), u.isLogistician(), u.isMissionManager(), u.isInKeycloak(), u.getVersion(), u.getJoinDate()
        );
    }

    public static JobTypeDto toDto(JobType jt) {
        UUID parentId = jt.getParent() != null ? jt.getParent().getId() : null;
        return new JobTypeDto(jt.getId(), jt.getName(), jt.getDescription(), jt.getArchetype(), parentId, jt.isActive(), jt.isLeadershipRole(), jt.getVersion());
    }

    public static SquadronDto toDto(Squadron s) {
        return new SquadronDto(s.getId(), s.getName(), s.getShorthand(), s.getDescription(), s.isActive(), s.getVersion());
    }

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

    public static Squadron toEntity(SquadronDto dto) {
        Squadron s = new Squadron();
        s.setId(dto.id());
        s.setName(dto.name());
        s.setShorthand(dto.shorthand());
        s.setDescription(dto.description());
        return s;
    }
}
