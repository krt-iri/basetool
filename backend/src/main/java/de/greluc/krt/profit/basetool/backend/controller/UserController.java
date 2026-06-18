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

package de.greluc.krt.profit.basetool.backend.controller;

import de.greluc.krt.profit.basetool.backend.mapper.OrgUnitMembershipMapper;
import de.greluc.krt.profit.basetool.backend.mapper.UserMapper;
import de.greluc.krt.profit.basetool.backend.model.PayoutPreference;
import de.greluc.krt.profit.basetool.backend.model.dto.MembershipDeltaRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.MembershipDeltaResponse;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.profit.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.backend.model.dto.UserDto;
import de.greluc.krt.profit.basetool.backend.service.AuthHelperService;
import de.greluc.krt.profit.basetool.backend.service.OrgUnitMembershipService;
import de.greluc.krt.profit.basetool.backend.service.UserService;
import de.greluc.krt.profit.basetool.backend.web.PaginationUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the local {@code app_user} mirror. The {@code /me} endpoints derive the user id
 * from the JWT — never from the URL — so a caller can never impersonate another user via this path.
 * {@code /attributes}, the logistician/mission-manager toggles and {@code DELETE} are admin-scoped.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Transactional
public class UserController {

  private static final Set<String> ALLOWED_SORT = Set.of("username", "email", "rank", "id");

  private final UserService userService;
  private final UserMapper userMapper;
  private final AuthHelperService authHelperService;
  private final OrgUnitMembershipService orgUnitMembershipService;
  private final OrgUnitMembershipMapper orgUnitMembershipMapper;

  /**
   * Paged user list. Open to every authenticated member because the participant pickers in the
   * mission editor consume it.
   *
   * @return paged user DTOs
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public PageResponse<UserDto> getAllUsers(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, ALLOWED_SORT, "username");
    Page<de.greluc.krt.profit.basetool.backend.model.User> p = userService.findAll(pageable);
    List<UserDto> content =
        p.getContent().stream().map(userMapper::toDto).map(this::redactForPeerIfNeeded).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lightweight typeahead projection (id + username + displayName). Widened by {@code
   * BANK_MANAGEMENT} (REQ-BANK-009): bank managers resolve grantees and holders via this lookup and
   * need not hold any org role (REQ-BANK-008).
   *
   * @return all users as reference DTOs
   */
  @GetMapping("/lookup")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER', 'BANK_MANAGEMENT')")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.profit.basetool.backend.model.dto.UserReferenceDto> lookupUsers() {
    return userService.findAllReference();
  }

  /**
   * Paged username/displayName substring search.
   *
   * @return paged user DTOs
   */
  @GetMapping("/search")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public PageResponse<UserDto> searchUsers(
      @RequestParam String query,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, ALLOWED_SORT, "username");
    Page<de.greluc.krt.profit.basetool.backend.model.User> p =
        userService.searchByUsername(query, pageable);
    List<UserDto> content =
        p.getContent().stream().map(userMapper::toDto).map(this::redactForPeerIfNeeded).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Returns the user DTO. Multi-tenancy: a non-admin caller asking for a user that belongs to a
   * foreign squadron always gets the peer-redacted shape, even when the caller carries {@code
   * ROLE_LOGISTICIAN} or {@code ROLE_OFFICER}. Without the squadron-scope gate an officer of
   * squadron A could fetch the email / real name of any user in squadron B by guessing a UUID (the
   * list endpoints are squadron-scoped via {@link
   * de.greluc.krt.profit.basetool.backend.service.UserService#findAll}; this {@code byId} path was
   * the only multi-tenancy hole left after the 2026-05-20 audit, finding H-3).
   *
   * @param id user id
   * @return the user DTO, peer-redacted for cross-squadron non-admin callers
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public UserDto getUserById(@PathVariable @NotNull UUID id) {
    de.greluc.krt.profit.basetool.backend.model.User user = userService.findById(id);
    UserDto dto = userMapper.toDto(user);
    if (isCrossSquadronNonAdmin(user)) {
      return redactToPeerShape(dto);
    }
    return redactForPeerIfNeeded(dto);
  }

  /**
   * Lists every org unit the given user is a member of, materialised as picker-optimised {@link
   * OrgUnitMembershipOptionDto} rows. Backs the R5.d owner-picker fragment: a Thymeleaf form with
   * an explicit target-user dropdown can populate its {@code <select>} of legal {@code
   * owningOrgUnitId} values directly from this endpoint, without having to thread membership data
   * through every page controller.
   *
   * <p>Access policy: open to every authenticated member. The endpoint reveals only the names and
   * shorthands of org units a target user belongs to — equivalent in sensitivity to the {@code
   * /lookup} typeahead, which is already broadly accessible. A non-admin cannot derive any
   * personally identifying data from the response (no display name, no email, no rank). Keeping the
   * surface symmetric with {@code /lookup} avoids forcing the picker fragment to branch on the
   * caller's role at render time.
   *
   * @param id the user id whose memberships to list; never {@code null}.
   * @return picker-friendly option DTOs sorted Staffel-first then SK alphabetical; never {@code
   *     null}, possibly empty when the user has no memberships.
   */
  @GetMapping("/{id}/memberships")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public List<OrgUnitMembershipOptionDto> getUserMemberships(@PathVariable @NotNull UUID id) {
    return orgUnitMembershipService.listOptionsForUser(id);
  }

  /**
   * Returns {@code true} when the caller is a non-admin and the target user belongs to a foreign
   * squadron (or to a squadron the caller cannot see via {@code OwnerScopeService}). Used to
   * tighten {@link #getUserById} to "same squadron or admin" for full-PII access. Users without a
   * squadron (admins, unassigned) are treated as cross-squadron for non-admin callers — full PII on
   * an unassigned account remains admin-only.
   *
   * @param user target user resolved by id; never {@code null}
   * @return {@code true} if the caller is a non-admin and the user is not in the caller's squadron
   *     scope
   */
  private boolean isCrossSquadronNonAdmin(
      @NotNull de.greluc.krt.profit.basetool.backend.model.User user) {
    if (authHelperService.isAdmin()) {
      return false;
    }
    // Post-R9 D3 (V101): the user's home Staffel is sourced from org_unit_membership — the legacy
    // User.squadron column was dropped.
    UUID userSquadronId =
        orgUnitMembershipService.findStaffelMembershipOrgUnitId(user.getId()).orElse(null);
    if (userSquadronId == null) {
      return true;
    }
    return !authHelperService.canSeeSquadron(userSquadronId);
  }

  /**
   * Returns the calling user's own record (derived from the JWT subject). The {@code @PreAuthorize}
   * is redundant with the {@link de.greluc.krt.profit.basetool.backend.config.SecurityConfig} URL
   * matcher that already gates {@code /api/v1/users/me} as {@code authenticated()}, but having it
   * on the handler keeps the guarantee local to this method — if the URL pattern is ever
   * refactored, the {@code @Jwt} binding here would NPE on anonymous instead of failing safely.
   * Audit finding L-2 (2026-05-20).
   *
   * @param jwt caller's JWT — never {@code null} thanks to the {@code @PreAuthorize}
   * @return the user DTO
   */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public UserDto getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    de.greluc.krt.profit.basetool.backend.model.User me =
        userService.findById(userService.getUserIdFromJwt(jwt));
    return withSelfEmail(userMapper.toDto(me), me);
  }

  /**
   * Updates the calling user's own description + displayName. The JWT identifies the row — no
   * impersonation possible.
   *
   * @param request update payload (carries the expected version)
   * @return the persisted DTO
   */
  @PutMapping("/me/description")
  @PreAuthorize("isAuthenticated()")
  public UserDto updateMyDescription(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody @jakarta.validation.Valid UserDescriptionRequest request) {
    de.greluc.krt.profit.basetool.backend.model.User me =
        userService.updateUserDescription(
            userService.getUserIdFromJwt(jwt),
            request.getDescription(),
            request.getDisplayName(),
            request.getVersion());
    return withSelfEmail(userMapper.toDto(me), me);
  }

  /**
   * Returns the calling user's personal default payout preference and the current optimistic-lock
   * version, backing the profile page's payout-preference selector. Derived from the JWT subject —
   * a caller can only ever read their own. A {@code null} preference means the user has made no
   * explicit choice yet (mission sign-up then falls back to {@code PAYOUT}, and the selector
   * pre-selects {@code PAYOUT}).
   *
   * @param jwt caller's JWT; never {@code null} thanks to the {@code @PreAuthorize}.
   * @return the current default payout preference (possibly {@code null}) plus the user-row
   *     version.
   */
  @GetMapping("/me/payout-preference")
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public MyPayoutPreferenceResponse getMyPayoutPreference(@AuthenticationPrincipal Jwt jwt) {
    de.greluc.krt.profit.basetool.backend.model.User me =
        userService.findById(userService.getUserIdFromJwt(jwt));
    return new MyPayoutPreferenceResponse(me.getDefaultPayoutPreference(), me.getVersion());
  }

  /**
   * Sets the calling user's personal default payout preference. The JWT identifies the row — no
   * impersonation possible. Carries the optimistic-lock version so a concurrent edit surfaces as a
   * 409 instead of a silent overwrite. The new value only pre-fills future mission sign-ups; it
   * does not rewrite existing participations.
   *
   * @param jwt caller's JWT; never {@code null} thanks to the {@code @PreAuthorize}.
   * @param request the new preference plus the expected version.
   * @return the persisted preference and the new version.
   */
  @PutMapping("/me/payout-preference")
  @PreAuthorize("isAuthenticated()")
  public MyPayoutPreferenceResponse updateMyPayoutPreference(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody @jakarta.validation.Valid MyPayoutPreferenceRequest request) {
    de.greluc.krt.profit.basetool.backend.model.User me =
        userService.updateUserDefaultPayoutPreference(
            userService.getUserIdFromJwt(jwt), request.preference(), request.version());
    return new MyPayoutPreferenceResponse(me.getDefaultPayoutPreference(), me.getVersion());
  }

  /**
   * Records that the calling user has read the given announcement (clears the unread badge).
   *
   * @param announcementId announcement just read
   * @return the persisted DTO
   */
  @PutMapping("/me/read-announcement/{announcementId}")
  @PreAuthorize("isAuthenticated()")
  public UserDto updateReadAnnouncement(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID announcementId) {
    de.greluc.krt.profit.basetool.backend.model.User me =
        userService.updateReadAnnouncement(userService.getUserIdFromJwt(jwt), announcementId);
    return withSelfEmail(userMapper.toDto(me), me);
  }

  /**
   * Admin/officer-only: edits an arbitrary user's attributes (rank, description, displayName,
   * joinDate). Carries optimistic-lock version in the body so concurrent admin edits surface a 409
   * instead of silently overwriting.
   *
   * @param id user id
   * @param request typed body (NOT query params — keeps user values out of access logs)
   * @return the persisted DTO
   */
  @PutMapping("/{id}/attributes")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDto updateUserAttributes(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid UserAttributesRequest request) {
    return userMapper.toDto(
        userService.updateUserAttributes(
            id,
            request.getRank(),
            request.getDescription(),
            request.getDisplayName(),
            request.getVersion(),
            request.getJoinDate()));
  }

  /**
   * Flips the {@code is_logistician} flag. The JWT-to-authorities converter promotes the flag to
   * {@code ROLE_LOGISTICIAN} on the next authentication.
   */
  @PatchMapping("/{id}/logistician")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDto updateLogisticianStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isLogistician) {
    return userMapper.toDto(userService.updateLogisticianStatus(id, isLogistician));
  }

  /** Flips the {@code is_mission_manager} flag (mirrors {@link #updateLogisticianStatus}). */
  @PatchMapping("/{id}/mission-manager")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDto updateMissionManagerStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isMissionManager) {
    return userMapper.toDto(userService.updateMissionManagerStatus(id, isMissionManager));
  }

  /**
   * Assigns the user to a squadron, or clears the assignment when {@code request.squadronId} is
   * {@code null}. Admin-only. Optimistic-locking via the {@code version} field of the body — two
   * admins editing the same user row simultaneously surface as 409 instead of one silently winning.
   *
   * @param id user primary key
   * @param request typed body carrying the target squadron id (nullable to clear) and the expected
   *     {@code @Version}
   * @return the persisted DTO
   */
  @PatchMapping("/{id}/squadron")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDto updateUserSquadron(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid UpdateUserSquadronRequest request) {
    return userMapper.toDto(
        userService.updateUserSquadron(id, request.squadronId(), request.version()));
  }

  /**
   * Body for {@link #updateUserSquadron}: the squadron the user should belong to (or {@code null}
   * to clear), plus the optimistic-lock version echoed back from the last read.
   *
   * @param squadronId target squadron id, or {@code null} to unassign
   * @param version the {@code @Version} of the user row the admin last fetched
   */
  public record UpdateUserSquadronRequest(
      @org.jetbrains.annotations.Nullable UUID squadronId,
      @jakarta.validation.constraints.NotNull Long version) {}

  /**
   * SPEZIALKOMMANDO_PLAN.md §7.4 single-POST membership-delta endpoint. Lets the admin member-edit
   * page persist every Staffel-assignment + flag-toggle + SK add / remove / patch as one atomic
   * transaction. Per-row optimistic-lock survives because every change record carries its own
   * {@code version}; an inconsistent batch (one row stale, the rest fresh) rolls back the whole
   * transaction and surfaces as a 409 — partial application is not exposed.
   *
   * @param id user primary key.
   * @param request the delta to apply; never {@code null}, but both halves may be {@code null} /
   *     empty.
   * @return the user's complete post-write membership list.
   */
  @PatchMapping("/{id}/memberships")
  @PreAuthorize("hasRole('ADMIN')")
  public MembershipDeltaResponse patchMemberships(
      @PathVariable @NotNull UUID id,
      @RequestBody @jakarta.validation.Valid MembershipDeltaRequest request) {
    return new MembershipDeltaResponse(
        userService.applyMembershipDelta(id, request).stream()
            .map(orgUnitMembershipMapper::toDto)
            .toList());
  }

  /**
   * ADMIN-only: deletes a user account along with all owned data (ships, inventory, refinery
   * orders, mission memberships).
   *
   * @param id user id
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteUser(@PathVariable @NotNull UUID id) {
    userService.deleteUser(id);
  }

  /** Body for {@link #updateUserAttributes}. */
  @lombok.Data
  public static class UserAttributesRequest {
    @jakarta.validation.constraints.NotNull private Integer rank;
    private String description;
    private String displayName;
    @jakarta.validation.constraints.NotNull private Long version;
    @org.jetbrains.annotations.Nullable private LocalDate joinDate;
  }

  /** Body for {@link #updateMyDescription}. */
  @lombok.Data
  public static class UserDescriptionRequest {
    private String description;
    private String displayName;
    @jakarta.validation.constraints.NotNull private Long version;
  }

  /**
   * Response for {@link #getMyPayoutPreference} / {@link #updateMyPayoutPreference}: the user's
   * current default payout preference (or {@code null} when never chosen) plus the user-row
   * optimistic-lock version the selector echoes back on save.
   *
   * @param defaultPayoutPreference the stored default, or {@code null} for "no explicit choice".
   * @param version the user row's current {@code @Version}.
   */
  public record MyPayoutPreferenceResponse(
      @org.jetbrains.annotations.Nullable PayoutPreference defaultPayoutPreference, Long version) {}

  /**
   * Body for {@link #updateMyPayoutPreference}: the new default payout preference and the expected
   * optimistic-lock version. The preference is {@code @NotNull} — the profile selector always posts
   * a concrete {@code PAYOUT} / {@code DONATE} (a user wanting the implicit default simply picks
   * {@code PAYOUT}); there is no API path that clears it back to {@code null}.
   *
   * @param preference the new default payout preference; never {@code null}.
   * @param version the {@code @Version} of the user row the caller last read; never {@code null}.
   */
  public record MyPayoutPreferenceRequest(
      @jakarta.validation.constraints.NotNull PayoutPreference preference,
      @jakarta.validation.constraints.NotNull Long version) {}

  /**
   * Strips the non-email PII that a peer (non-Officer, non-Admin) does not need to see. Officers
   * and admins get the DTO unchanged; plain members get the slim peer shape. {@code email} is no
   * longer governed here at all — {@link UserMapper#toDto(User)} omits it for every projection (it
   * is re-added only on the {@code /me*} self path via {@link #withSelfEmail}), so even an
   * officer/admin never receives a peer's email through this controller. This helper now only hides
   * the remaining peer-irrelevant fields from plain members. Audit finding H-4: previously any
   * SQUADRON_MEMBER could paginate {@code /api/v1/users/search} and harvest every member's email.
   *
   * <p>The peer view keeps {@code id}, {@code username}, {@code displayName}, {@code
   * effectiveName}, {@code rank}, {@code inKeycloak}, {@code squadron}, {@code version} — enough
   * for the participant pickers in the mission editor to identify peers visually; drops {@code
   * description}, {@code roles}, {@code permissions}, {@code lastReadAnnouncementId}, {@code
   * isLogistician}, {@code isMissionManager}, {@code joinDate} (and {@code email}, already {@code
   * null} from the mapper).
   *
   * @param dto the persisted user DTO
   * @return the redacted DTO for non-elevated callers, or the original for officer/admin
   */
  private UserDto redactForPeerIfNeeded(UserDto dto) {
    if (dto == null || authHelperService.isLogisticianOrAbove()) {
      return dto;
    }
    return redactToPeerShape(dto);
  }

  /**
   * Returns the slim peer view of {@code dto} unconditionally — drops PII fields ({@code email},
   * description, roles, permissions, flags, joinDate, lastReadAnnouncementId) and keeps the public
   * callsign tuple. Used by {@link #getUserById} for the cross-squadron non-admin path (audit
   * finding H-3), where role-based escalation does NOT widen the view — an officer of squadron A
   * must not see PII of squadron B's members.
   *
   * @param dto persisted user DTO; never {@code null}
   * @return the slim peer-view DTO
   */
  private UserDto redactToPeerShape(@NotNull UserDto dto) {
    return new UserDto(
        dto.id(),
        dto.username(),
        dto.displayName(),
        dto.effectiveName(),
        null, // email
        dto.rank(),
        null, // description
        null, // roles
        null, // permissions
        null, // lastReadAnnouncementId
        false, // isLogistician
        false, // isMissionManager
        dto.inKeycloak(),
        dto.squadron(),
        dto.version(),
        null // joinDate
        );
  }

  /**
   * Re-attaches the caller's own {@code email} to a DTO produced by {@link UserMapper#toDto(User)},
   * which deliberately omits it. Used only by the {@code /me*} self endpoints: a user may always
   * see their own email in their own profile, while {@code toDto} keeps it {@code null} for every
   * other (peer / list / admin) projection so a user's email never reaches anyone else. All
   * non-email fields are copied straight from {@code dto}.
   *
   * @param dto the email-free DTO from the mapper; never {@code null}
   * @param user the caller's own entity, the source of the email; never {@code null}
   * @return a copy of {@code dto} with {@code email} populated from {@code user}
   */
  private UserDto withSelfEmail(
      @NotNull UserDto dto, @NotNull de.greluc.krt.profit.basetool.backend.model.User user) {
    return new UserDto(
        dto.id(),
        dto.username(),
        dto.displayName(),
        dto.effectiveName(),
        user.getEmail(),
        dto.rank(),
        dto.description(),
        dto.roles(),
        dto.permissions(),
        dto.lastReadAnnouncementId(),
        dto.isLogistician(),
        dto.isMissionManager(),
        dto.inKeycloak(),
        dto.squadron(),
        dto.version(),
        dto.joinDate());
  }
}
