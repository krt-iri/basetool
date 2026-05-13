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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Transactional
public class UserController {

  private static final Set<String> ALLOWED_SORT =
      Set.of("username", "firstName", "lastName", "email", "rank", "id");

  private final UserService userService;
  private final UserMapper userMapper;

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

  @GetMapping("/lookup")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public List<de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto> lookupUsers() {
    return userService.findAllReference();
  }

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

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'SQUADRON_MEMBER', 'MEMBER')")
  @Transactional(readOnly = true)
  public UserDto getUserById(@PathVariable @NotNull UUID id) {
    return userMapper.toDto(userService.findById(id));
  }

  @GetMapping("/me")
  @Transactional(readOnly = true)
  public UserDto getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    return userMapper.toDto(userService.findById(userService.getUserIdFromJwt(jwt)));
  }

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

  @PutMapping("/me/read-announcement/{announcementId}")
  @PreAuthorize("isAuthenticated()")
  public UserDto updateReadAnnouncement(
      @AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID announcementId) {
    return userMapper.toDto(
        userService.updateReadAnnouncement(userService.getUserIdFromJwt(jwt), announcementId));
  }

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

  @PatchMapping("/{id}/logistician")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public UserDto updateLogisticianStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isLogistician) {
    return userMapper.toDto(userService.updateLogisticianStatus(id, isLogistician));
  }

  @PatchMapping("/{id}/mission-manager")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public UserDto updateMissionManagerStatus(
      @PathVariable @NotNull UUID id, @RequestParam boolean isMissionManager) {
    return userMapper.toDto(userService.updateMissionManagerStatus(id, isMissionManager));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteUser(@PathVariable @NotNull UUID id) {
    userService.deleteUser(id);
  }

  @lombok.Data
  public static class UserAttributesRequest {
    @jakarta.validation.constraints.NotNull private Integer rank;
    private String description;
    private String displayName;
    @jakarta.validation.constraints.NotNull private Long version;
    @org.jetbrains.annotations.Nullable private LocalDate joinDate;
  }

  @lombok.Data
  public static class UserDescriptionRequest {
    private String description;
    private String displayName;
    @jakarta.validation.constraints.NotNull private Long version;
  }
}
