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

import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.Role;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.SquadronReferenceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.UserDto;
import de.greluc.krt.profit.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * MapStruct mapper between {@link User} entities and DTOs.
 *
 * <p>After R9 Steps 4+5 the {@link User} entity no longer carries the legacy {@code squadron},
 * {@code isLogistician}, {@code isMissionManager} columns — the {@code org_unit_membership} row is
 * now the single source of truth. The DTO contract still exposes those three fields (so the
 * frontend mirror and existing API consumers stay stable); the mapper derives them by reading the
 * caller's single Staffel membership through {@link OrgUnitMembershipRepository}. A user without a
 * Staffel membership row (admin / guest) maps to {@code squadron = null}, {@code isLogistician =
 * false}, {@code isMissionManager = false}, matching the pre-R9 semantics for "unassigned".
 *
 * <p>Implemented as an abstract class so MapStruct can field-inject the helper repositories. The
 * generated subclass overrides {@link #toDto(User)} with the field copy and forwards through {@link
 * #resolveSquadron(User)} / {@link #resolveLogistician(User)} / {@link
 * #resolveMissionManager(User)} for the membership-derived projections.
 */
@Mapper(
    componentModel = "spring",
    uses = {SquadronMapper.class})
public abstract class UserMapper {

  // Field injection mirrors MissionMapper / RefineryOrderMapper — the MapStruct annotation
  // processor generates the subclass with a default constructor, so the helper repositories must
  // come in via field-level @Autowired.
  @Autowired protected OrgUnitMembershipRepository membershipRepository;

  @Autowired protected SquadronRepository squadronRepository;

  /**
   * Request-attribute key under which {@link #loadStaffelMembership(User)} memoises the per-user
   * Staffel-membership lookup for the duration of the current HTTP request. Without it, every
   * {@link #toDto(User)} call fires the same {@code findAllByIdUserIdAndKind} derived query three
   * times (once each from {@link #resolveSquadron(User)} / {@link #resolveLogistician(User)} /
   * {@link #resolveMissionManager(User)}) because a JPQL derived query is not served from
   * Hibernate's L1 cache — three real round-trips per user, multiplied across a whole page on the
   * member-facing user list / search endpoints. The memo collapses that to one lookup per distinct
   * user per request and is keyed by user id.
   */
  private static final String MEMBERSHIP_CACHE_ATTR =
      UserMapper.class.getName() + ".staffelMembershipByUserId";

  /**
   * Projects a {@link User} entity to its outbound DTO. The {@code squadron}, {@code
   * isLogistician}, {@code isMissionManager} fields are sourced from the user's Staffel membership
   * row (post-R9 D3) — see the class-level Javadoc for the unassigned-user fallback.
   *
   * <p><strong>{@code email} is deliberately NOT mapped</strong> ({@code ignore = true}) so it is
   * {@code null} on every DTO this method produces. Email is a profile-only field: it may be shown
   * only to the user themselves in their own profile, never to any other user. Because every nested
   * mapper ({@code MissionMapper}, {@code ShipMapper}, {@code JobOrderMapper}) and every list /
   * detail / admin endpoint funnels its {@code User → UserDto} conversion through this single
   * method, omitting email here closes all peer-exposure paths at once and keeps new endpoints safe
   * by default. The only caller that needs the email — the user's own {@code /api/v1/users/me*}
   * view — re-adds it explicitly (see {@code UserController}); do NOT add a second {@code User →
   * UserDto} mapping method here, as that would make the nested-mapper delegation ambiguous.
   *
   * @param user the user entity to project; {@code null} maps to {@code null}.
   * @return the populated DTO with {@code email == null}, or {@code null} when {@code user} is
   *     {@code null}.
   */
  @Mapping(target = "email", ignore = true)
  @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
  @Mapping(target = "permissions", expression = "java(permissions(user.getRoles()))")
  @Mapping(target = "isLogistician", expression = "java(resolveLogistician(user))")
  @Mapping(target = "isMissionManager", expression = "java(resolveMissionManager(user))")
  @Mapping(target = "squadron", expression = "java(resolveSquadron(user))")
  @Mapping(target = "discordLinked", expression = "java(resolveDiscordLinked(user))")
  public abstract UserDto toDto(User user);

  /** Narrow reference DTO (id + display name) used wherever the full user payload is overkill. */
  public abstract UserReferenceDto toReferenceDto(User user);

  /** MapStruct default - flattens the user's roles to a set of role-name strings. */
  protected Set<String> roleNames(Set<Role> roles) {
    if (roles == null) {
      return Collections.emptySet();
    }
    return roles.stream().map(Role::getName).collect(Collectors.toSet());
  }

  /** MapStruct default - flattens the permissions of every role the user owns into one set. */
  protected Set<String> permissions(Set<Role> roles) {
    if (roles == null) {
      return Collections.emptySet();
    }
    return roles.stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
  }

  /**
   * Resolves the user's home Staffel for the DTO's {@code squadron} projection. Reads the single
   * Staffel membership row (V95 partial unique index guarantees at most one) and resolves the
   * {@link Squadron} entity through {@link SquadronRepository#findById(Object)}. Returns {@code
   * null} when the user has no Staffel membership (admins / guests) or when the user itself is
   * {@code null} (defensive — MapStruct's generated mapping passes {@code null} for the
   * null-source-null-target contract).
   *
   * @param user the user being projected; may be {@code null}.
   * @return the user's Staffel reference DTO, or {@code null} when no Staffel membership exists.
   */
  protected SquadronReferenceDto resolveSquadron(User user) {
    if (user == null || user.getId() == null) {
      return null;
    }
    return loadStaffelMembership(user)
        .flatMap(m -> squadronRepository.findById(m.getId().getOrgUnitId()))
        .map(s -> new SquadronReferenceDto(s.getId(), s.getName(), s.getShorthand()))
        .orElse(null);
  }

  /**
   * Resolves the user's effective {@code isLogistician} flag from the Staffel membership row. A
   * user without a Staffel membership returns {@code false} — matches the pre-R9 "no row, no flag"
   * behaviour.
   *
   * @param user the user being projected; may be {@code null}.
   * @return {@code true} iff the user's Staffel membership carries {@code isLogistician = true}.
   */
  protected Boolean resolveLogistician(User user) {
    if (user == null || user.getId() == null) {
      return Boolean.FALSE;
    }
    return loadStaffelMembership(user).map(OrgUnitMembership::isLogistician).orElse(Boolean.FALSE);
  }

  /**
   * Resolves the user's effective {@code isMissionManager} flag from the Staffel membership row.
   * Mirrors {@link #resolveLogistician(User)} semantics — no membership row means {@code false}.
   *
   * @param user the user being projected; may be {@code null}.
   * @return {@code true} iff the user's Staffel membership carries {@code isMissionManager = true}.
   */
  protected Boolean resolveMissionManager(User user) {
    if (user == null || user.getId() == null) {
      return Boolean.FALSE;
    }
    return loadStaffelMembership(user)
        .map(OrgUnitMembership::isMissionManager)
        .orElse(Boolean.FALSE);
  }

  /**
   * Resolves the {@code discordLinked} indicator for the DTO. Returns {@code true} iff the user has
   * a non-blank {@code discord_user_id} — i.e. a Discord account is federated to this Basetool
   * account (REQ-DATA-006). The raw snowflake itself is never copied into the DTO; only this
   * boolean fact leaves the backend, and the admin member-management page renders it as the Discord
   * column (REQ-SEC-019). Independent of the Staffel membership, so it needs no membership lookup.
   *
   * @param user the user being projected; may be {@code null}.
   * @return {@code true} iff a Discord account is linked, {@code false} otherwise.
   */
  protected Boolean resolveDiscordLinked(User user) {
    if (user == null) {
      return Boolean.FALSE;
    }
    String discordUserId = user.getDiscordUserId();
    return discordUserId != null && !discordUserId.isBlank();
  }

  /**
   * Reads the at-most-one Staffel membership of the user. Tolerates the (illegal) corruption state
   * where multiple Staffel memberships exist for the same user — picks the first row deterministic
   * by repository order — so the mapper never throws on a bad-data edge case. The V95 partial
   * unique index prevents the corruption in normal operation.
   *
   * <p>Memoised per request: the three derived-field resolvers each call this for the same user, so
   * without caching the underlying derived query runs three times per {@code toDto}. The result is
   * cached on the {@link RequestAttributes} keyed by user id ({@link #MEMBERSHIP_CACHE_ATTR}), so
   * it runs at most once per distinct user per request. Outside an HTTP request (e.g. a scheduled
   * task that maps a user) there is no request scope, so it falls back to the direct query with no
   * memo. Only eagerly-loaded scalar fields of the returned membership are read by the callers, so
   * a value surviving into a later transaction within the same request is still safe to read.
   *
   * <p>The memo assumes a user's Staffel membership is <em>immutable for the duration of the
   * request</em>: it is populated lazily on first read, so a write endpoint that mutates the
   * membership before mapping the affected user sees the fresh value (the memo is still empty at
   * that point). A hypothetical endpoint that maps a user, mutates that same user's membership,
   * then re-maps the same user in the same request would observe the pre-mutation snapshot — no
   * such flow exists today; one that needs it must evict {@link #MEMBERSHIP_CACHE_ATTR} after the
   * mutation.
   *
   * @param user the user whose Staffel membership to load; never {@code null}.
   * @return the single Staffel membership row if any.
   */
  private Optional<OrgUnitMembership> loadStaffelMembership(User user) {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return queryStaffelMembership(user);
    }
    @SuppressWarnings("unchecked")
    Map<UUID, Optional<OrgUnitMembership>> cache =
        (Map<UUID, Optional<OrgUnitMembership>>)
            attrs.getAttribute(MEMBERSHIP_CACHE_ATTR, RequestAttributes.SCOPE_REQUEST);
    if (cache == null) {
      cache = new HashMap<>();
      attrs.setAttribute(MEMBERSHIP_CACHE_ATTR, cache, RequestAttributes.SCOPE_REQUEST);
    }
    return cache.computeIfAbsent(user.getId(), id -> queryStaffelMembership(user));
  }

  /**
   * Executes the actual Staffel-membership lookup behind {@link #loadStaffelMembership(User)},
   * without the request-scoped memo. Tolerates the (illegal) multi-row corruption state by picking
   * the first row deterministically.
   *
   * @param user the user whose Staffel membership to query; never {@code null}.
   * @return the single Staffel membership row if any.
   */
  private Optional<OrgUnitMembership> queryStaffelMembership(User user) {
    List<OrgUnitMembership> rows =
        membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }
}
