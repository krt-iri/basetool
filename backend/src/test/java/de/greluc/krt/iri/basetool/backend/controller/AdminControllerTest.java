package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.RoleMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RoleDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.service.RoleService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pure-Mockito unit tests for {@link AdminController}. The controller is small but each endpoint
 * touches a different MapStruct mapper — the role/user-DTO conversion is the spot where an
 * accidental copy-paste during a future refactor would silently leak a JPA entity through the
 * REST boundary (the ArchUnit rule catches the static return type but not the entity *inside*
 * the mapper output). These tests pin the explicit toDto-call on the response path so the
 * conversion stays in place.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  @Mock private RoleService roleService;
  @Mock private UserService userService;
  @Mock private RoleMapper roleMapper;
  @Mock private UserMapper userMapper;

  @InjectMocks private AdminController controller;

  private static UserDto userDto(UUID id) {
    return new UserDto(
        id, "username", null, "Effective", null, null, null, 1, null, java.util.Set.of(),
        java.util.Set.of(), null, false, false, true, 1L, null);
  }

  // ── /api/v1/admin/roles ───────────────────────────────────────────────

  @Test
  void getAllRoles_pagesEntitiesThroughMapperIntoPageResponse() {
    Role roleA = new Role();
    Role roleB = new Role();
    RoleDto dtoA = new RoleDto(1L, "ADMIN", "Admin role", Set.of(), 1L);
    RoleDto dtoB = new RoleDto(2L, "OFFICER", "Officer role", Set.of(), 1L);
    Page<Role> page =
        new PageImpl<>(
            List.of(roleA, roleB),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name")),
            2);
    when(roleService.getAllRoles(any(Pageable.class))).thenReturn(page);
    when(roleMapper.toDto(roleA)).thenReturn(dtoA);
    when(roleMapper.toDto(roleB)).thenReturn(dtoB);

    PageResponse<RoleDto> result = controller.getAllRoles(0, 20, "name,asc");

    // The PageResponse must carry the MAPPED DTOs, never the raw entities — otherwise the
    // ArchUnit-enforced "no entity at controller boundary" contract would still be honoured
    // statically (return type is Page<RoleDto>) while the actual payload leaked JPA proxies.
    assertThat(result.content()).containsExactly(dtoA, dtoB);
    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(2L);
    assertThat(result.totalPages()).isEqualTo(1);
    assertThat(result.sort()).containsExactly("name,asc");
    verify(roleService).getAllRoles(any(Pageable.class));
  }

  @Test
  void getAllRoles_withDefaults_returnsEmptyContentWhenNoRoles() {
    Page<Role> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    when(roleService.getAllRoles(any(Pageable.class))).thenReturn(page);

    PageResponse<RoleDto> result = controller.getAllRoles(null, null, null);

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isZero();
    verify(roleService).getAllRoles(any(Pageable.class));
  }

  // ── PUT /api/v1/admin/roles/{name}/permissions ────────────────────────

  @Test
  void updatePermissions_mapsServiceResultThroughRoleMapper() {
    Role updated = new Role();
    RoleDto dto = new RoleDto(2L, "OFFICER", "Officer role", Set.of("READ", "WRITE"), 1L);
    Set<String> newPerms = Set.of("READ", "WRITE");
    when(roleService.updatePermissions("OFFICER", newPerms)).thenReturn(updated);
    when(roleMapper.toDto(updated)).thenReturn(dto);

    RoleDto result = controller.updatePermissions("OFFICER", newPerms);

    assertThat(result).isSameAs(dto);
    verify(roleService).updatePermissions("OFFICER", newPerms);
    verify(roleMapper).toDto(updated);
  }

  // ── PUT /api/v1/admin/roles/{name}/description ────────────────────────

  @Test
  void updateRoleDescription_mapsServiceResultThroughRoleMapper() {
    Role updated = new Role();
    RoleDto dto = new RoleDto(1L, "ADMIN", "New text", Set.of(), 1L);
    when(roleService.updateRoleDescription("ADMIN", "New text")).thenReturn(updated);
    when(roleMapper.toDto(updated)).thenReturn(dto);

    RoleDto result = controller.updateRoleDescription("ADMIN", "New text");

    assertThat(result).isSameAs(dto);
    verify(roleService).updateRoleDescription("ADMIN", "New text");
  }

  // ── PUT /api/v1/admin/users/{id}/attributes ───────────────────────────

  @Test
  void updateUserAttributes_unpacksRequestRecordAndForwardsToService() {
    UUID userId = UUID.randomUUID();
    LocalDate joinDate = LocalDate.of(2024, 1, 15);
    AdminController.AdminUserAttributesRequest request =
        new AdminController.AdminUserAttributesRequest(
            12, "Test description", "Display name", 3L, joinDate);
    User updated = new User();
    UserDto dto = userDto(userId);
    when(userService.updateUserAttributes(userId, 12, "Test description", "Display name", 3L,
            joinDate))
        .thenReturn(updated);
    when(userMapper.toDto(updated)).thenReturn(dto);

    UserDto result = controller.updateUserAttributes(userId, request);

    // The controller must spread the record's fields into the service's positional argument list
    // exactly as documented — otherwise an admin's "set rank=12" edit could silently apply to
    // displayName or vice-versa. Verify-call pins the exact argument order.
    assertThat(result).isSameAs(dto);
    verify(userService)
        .updateUserAttributes(userId, 12, "Test description", "Display name", 3L, joinDate);
    verify(userMapper).toDto(updated);
  }

  @Test
  void updateUserAttributes_passesNullableOptionalFieldsAsIs() {
    // rank, description, displayName and joinDate are all nullable in the record; the controller
    // must forward null verbatim so the service decides whether a null means "leave unchanged"
    // (its actual contract) — not the controller pre-coercing null into an empty string.
    UUID userId = UUID.randomUUID();
    AdminController.AdminUserAttributesRequest request =
        new AdminController.AdminUserAttributesRequest(null, null, null, 1L, null);
    User updated = new User();
    UserDto dto = userDto(userId);
    when(userService.updateUserAttributes(userId, null, null, null, 1L, null)).thenReturn(updated);
    when(userMapper.toDto(updated)).thenReturn(dto);

    UserDto result = controller.updateUserAttributes(userId, request);

    assertThat(result).isSameAs(dto);
    verify(userService).updateUserAttributes(userId, null, null, null, 1L, null);
  }
}
