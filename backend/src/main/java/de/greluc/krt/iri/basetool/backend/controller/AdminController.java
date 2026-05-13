package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.RoleMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RoleDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.service.RoleService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class AdminController {

  private final RoleService roleService;
  private final UserService userService;
  private final RoleMapper roleMapper;
  private final UserMapper userMapper;

  @GetMapping("/roles")
  public PageResponse<RoleDto> getAllRoles(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id"), "name");
    Page<Role> p = roleService.getAllRoles(pageable);
    List<RoleDto> content = p.getContent().stream().map(roleMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  @PutMapping("/roles/{name}/permissions")
  public RoleDto updatePermissions(
      @PathVariable @NotNull String name, @RequestBody @NotNull Set<String> permissions) {
    return roleMapper.toDto(roleService.updatePermissions(name, permissions));
  }

  @PutMapping("/roles/{name}/description")
  public RoleDto updateRoleDescription(
      @PathVariable @NotNull String name, @RequestBody @NotNull String description) {
    return roleMapper.toDto(roleService.updateRoleDescription(name, description));
  }

  @PutMapping("/users/{id}/attributes")
  public UserDto updateUserAttributes(
      @PathVariable @NotNull UUID id,
      @RequestBody @Valid @NotNull AdminUserAttributesRequest request) {
    return userMapper.toDto(
        userService.updateUserAttributes(
            id,
            request.rank(),
            request.description(),
            request.displayName(),
            request.version(),
            request.joinDate()));
  }

  /**
   * Body for {@code PUT /api/v1/admin/users/{id}/attributes}. Moves the four user-controlled values
   * out of the query string (where they leak into access logs and browser history) into a typed,
   * validated request body.
   */
  public record AdminUserAttributesRequest(
      Integer rank,
      String description,
      String displayName,
      @jakarta.validation.constraints.NotNull Long version,
      LocalDate joinDate) {}
}
