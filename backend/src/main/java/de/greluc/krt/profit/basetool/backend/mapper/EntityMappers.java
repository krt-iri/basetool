/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.mapper;

import de.greluc.krt.profit.basetool.backend.model.JobType;
import de.greluc.krt.profit.basetool.backend.model.Role;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.profit.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.profit.basetool.backend.model.dto.UserDto;
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
   *
   * <p>After R9 Steps 4+5 the {@link User} entity no longer carries the legacy {@code squadron},
   * {@code isLogistician}, {@code isMissionManager} columns — those fields land on the DTO as
   * {@code null} / {@code false} respectively when projected through this static utility. Callers
   * that need the membership-derived projection must use the Spring-managed {@link UserMapper} bean
   * instead, which loads the Staffel membership row to populate the three legacy fields. This
   * static helper exists only for the few legacy unit-test fixtures that wire User entities by hand
   * and never had a membership table to read from.
   */
  public static UserDto toDto(User u) {
    Set<String> roleNames = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    Set<String> permissions =
        u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
    return new UserDto(
        u.getId(),
        u.getUsername(),
        u.getDisplayName(),
        u.getEffectiveName(),
        u.getEmail(),
        u.getRank(),
        u.getDescription(),
        roleNames,
        permissions,
        u.getLastReadAnnouncementId(),
        Boolean.FALSE,
        Boolean.FALSE,
        u.isInKeycloak(),
        null,
        java.util.List.of(),
        u.getVersion(),
        u.getJoinDate(),
        u.getDiscordUserId() != null && !u.getDiscordUserId().isBlank());
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
        s.getId(),
        s.getName(),
        s.getShorthand(),
        s.getDescription(),
        s.isActive(),
        s.isPromotionEnabled(),
        s.isProfitEligible(),
        s.getVersion());
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
