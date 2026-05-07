package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    @Cacheable(cacheNames = CacheConfig.ROLES_CACHE)
    public Page<Role> getAllRoles(@NotNull Pageable pageable) {
        return roleRepository.findAll(pageable);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ROLES_CACHE, allEntries = true)
    public Role updatePermissions(@NotNull String roleName, @NotNull Set<String> permissions) {
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Role not found"));
        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ROLES_CACHE, allEntries = true)
    public Role updateRoleDescription(@NotNull String roleName, @NotNull String description) {
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Role not found"));
        role.setDescription(description);
        return roleRepository.save(role);
    }
}
