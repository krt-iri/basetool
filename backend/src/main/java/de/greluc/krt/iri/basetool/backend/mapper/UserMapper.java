package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

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
   * Projects a {@link User} entity to its full outbound DTO. The {@code squadron}, {@code
   * isLogistician}, {@code isMissionManager} fields are sourced from the user's Staffel membership
   * row (post-R9 D3) — see the class-level Javadoc for the unassigned-user fallback.
   *
   * @param user the user entity to project; {@code null} maps to {@code null}.
   * @return the populated DTO, or {@code null} when {@code user} is {@code null}.
   */
  @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
  @Mapping(target = "permissions", expression = "java(permissions(user.getRoles()))")
  @Mapping(target = "isLogistician", expression = "java(resolveLogistician(user))")
  @Mapping(target = "isMissionManager", expression = "java(resolveMissionManager(user))")
  @Mapping(target = "squadron", expression = "java(resolveSquadron(user))")
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
   * Reads the at-most-one Staffel membership of the user. Tolerates the (illegal) corruption state
   * where multiple Staffel memberships exist for the same user — picks the first row deterministic
   * by repository order — so the mapper never throws on a bad-data edge case. The V95 partial
   * unique index prevents the corruption in normal operation.
   *
   * @param user the user whose Staffel membership to load; never {@code null}.
   * @return the single Staffel membership row if any.
   */
  private Optional<OrgUnitMembership> loadStaffelMembership(User user) {
    List<OrgUnitMembership> rows =
        membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }
}
