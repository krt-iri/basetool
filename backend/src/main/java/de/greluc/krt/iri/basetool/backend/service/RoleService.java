package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the {@code role} table that holds the local copy of every Keycloak realm role plus the
 * project-specific permission set attached to each role.
 *
 * <p>The role names are populated by {@link
 * de.greluc.krt.iri.basetool.backend.config.DataInitializer} at boot (matched by {@code code}, not
 * by {@code name} — see CLAUDE.md). This service only handles the editable subset: description and
 * permission set. Cache is the {@code roles} cache, evicted on every write so a refreshed
 * permission set takes effect immediately for the next authentication.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

  private final RoleRepository roleRepository;

  /**
   * Paged role list.
   *
   * @param pageable page request
   * @return cached page result
   */
  @Cacheable(cacheNames = CacheConfig.ROLES_CACHE)
  public Page<Role> getAllRoles(@NotNull Pageable pageable) {
    return roleRepository.findAll(pageable);
  }

  /**
   * Replaces the permission set for the named role. Used by the role-management page; the
   * JWT-to-authorities converter re-reads permissions on every authentication so the change
   * propagates without a server restart.
   *
   * @param roleName role display name (looked up case-sensitively via repository's findByName)
   * @param permissions new permission set
   * @return the persisted role
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no role matches
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.ROLES_CACHE, allEntries = true)
  public Role updatePermissions(@NotNull String roleName, @NotNull Set<String> permissions) {
    Role role =
        roleRepository
            .findByName(roleName)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "Role not found"));
    role.setPermissions(permissions);
    return roleRepository.save(role);
  }

  /**
   * Updates the descriptive text for a role.
   *
   * @param roleName role display name
   * @param description new description
   * @return the persisted role
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no role matches
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.ROLES_CACHE, allEntries = true)
  public Role updateRoleDescription(@NotNull String roleName, @NotNull String description) {
    Role role =
        roleRepository
            .findByName(roleName)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "Role not found"));
    role.setDescription(description);
    return roleRepository.save(role);
  }
}
