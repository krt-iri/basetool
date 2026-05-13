package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
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

  private static final Set<String> ALLOWED_SORT =
      Set.of("username", "firstName", "lastName", "email", "rank", "id");

  private final UserService userService;
  private final UserMapper userMapper;

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
    Page<de.greluc.krt.iri.basetool.backend.model.User> p = userService.findAll(pageable);
    List<UserDto> content = p.getContent().stream().map(userMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lightweight typeahead projection (id + username + displayName).
   *
   * @return all users as reference DTOs
   */
  @GetMapping("/lookup")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto> lookupUsers() {
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
    Page<de.greluc.krt.iri.basetool.backend.model.User> p =
        userService.searchByUsername(query, pageable);
    List<UserDto> content = p.getContent().stream().map(userMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Returns the user DTO.
   *
   * @param id user id
   * @return the user DTO
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public UserDto getUserById(@PathVariable @NotNull UUID id) {
    return userMapper.toDto(userService.findById(id));
  }

  /**
   * Returns the calling user's own record (derived from the JWT subject).
   *
   * @return the user DTO
   */
  @GetMapping("/me")
  @Transactional(readOnly = true)
  public UserDto getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    return userMapper.toDto(userService.findById(userService.getUserIdFromJwt(jwt)));
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
    return userMapper.toDto(
        userService.updateUserDescription(
            userService.getUserIdFromJwt(jwt),
            request.getDescription(),
            request.getDisplayName(),
            request.getVersion()));
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
    return userMapper.toDto(
        userService.updateReadAnnouncement(userService.getUserIdFromJwt(jwt), announcementId));
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
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
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
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public UserDto updateLogisticianStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isLogistician) {
    return userMapper.toDto(userService.updateLogisticianStatus(id, isLogistician));
  }

  /** Flips the {@code is_mission_manager} flag (mirrors {@link #updateLogisticianStatus}). */
  @PatchMapping("/{id}/mission-manager")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public UserDto updateMissionManagerStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isMissionManager) {
    return userMapper.toDto(userService.updateMissionManagerStatus(id, isMissionManager));
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
}
