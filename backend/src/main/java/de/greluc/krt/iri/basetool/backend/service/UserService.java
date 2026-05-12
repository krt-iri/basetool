package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import de.greluc.krt.iri.basetool.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ShipRepository shipRepository;
    private final RefineryOrderRepository refineryOrderRepository;
    private final MissionRepository missionRepository;
    private final JobOrderRepository jobOrderRepository;
    private final MissionParticipantRepository missionParticipantRepository;
    private final AuthHelperService authHelperService;

    public boolean isUsernameOrDisplayNameTaken(@NotNull String name) {
        return !findMatchesByExactName(name).isEmpty();
    }

    /**
     * Resolves a free-text participant name to existing users by case-insensitive exact match
     * on {@code username} or {@code displayName}. The input is trimmed. An empty or blank name
     * yields an empty result without hitting the database.
     *
     * <p>Used by participant-add flows to translate free-text input (when the user did not pick
     * an entry from the autocomplete dropdown) into a concrete user reference, so that a member
     * is correctly linked instead of being (wrongly) rejected as a duplicate guest name.
     */
    @NotNull
    public List<User> findMatchesByExactName(@NotNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllByUsernameIgnoreCaseOrDisplayNameIgnoreCase(trimmed, trimmed);
    }

    @NotNull
    public UUID getUserIdFromJwt(@NotNull Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null) {
            // The OIDC standard requires `sub` on every ID token. A missing subject
            // indicates a misconfigured authorization server. Refuse rather than
            // falling back to a different claim and silently identifying users by
            // a value an admin might rename in Keycloak.
            log.error("JWT has no subject (sub). Refusing the request. Claims: {}", jwt.getClaims());
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "JWT subject (sub) must be present");
        }

        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            // Standard Keycloak issues UUIDs as subjects. A non-UUID sub is a
            // configuration deviation; deriving a UUID via UUID.nameUUIDFromBytes
            // would mix up identities (renaming the underlying value, two realms
            // with similar usernames, casing differences, ...). Fail-closed.
            log.error("JWT subject is not a valid UUID: '{}'. Refusing the request to avoid identity mix-up.", sub);
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "JWT subject must be a UUID");
        }
    }

    @Transactional
    @NotNull
    public User syncUser(@NotNull Jwt jwt) {
        final UUID finalUserId = getUserIdFromJwt(jwt);
        String username = jwt.getClaimAsString("preferred_username");

        Optional<User> existingUser = userRepository.findById(finalUserId);
        if (existingUser.isEmpty() && username != null) {
            existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent()) {
                log.warn("User lookup by ID {} failed, but found by username {}. associating session with existing user.", finalUserId, username);
            }
        }

        User user = existingUser.orElseGet(() -> {
            User u = new User();
            u.setId(finalUserId);
            return u;
        });

        boolean changed = false;

        if (!Objects.equals(user.getUsername(), username)) {
            user.setUsername(username);
            changed = true;
        }

        String firstName = jwt.getClaimAsString("given_name");
        if (!Objects.equals(user.getFirstName(), firstName)) {
            user.setFirstName(firstName);
            changed = true;
        }

        String lastName = jwt.getClaimAsString("family_name");
        if (!Objects.equals(user.getLastName(), lastName)) {
            user.setLastName(lastName);
            changed = true;
        }

        String email = jwt.getClaimAsString("email");
        if (!Objects.equals(user.getEmail(), email)) {
            user.setEmail(email);
            changed = true;
        }

        // Sync Roles
        Set<String> keycloakRoles = extractRolesFromJwt(jwt);
        Set<Role> localRoles = mapRoles(keycloakRoles);

        if (!user.getRoles().equals(localRoles)) {
            user.setRoles(localRoles);
            changed = true;
        }

        if (changed || user.isNew()) {
            return userRepository.save(user);
        }
        
        return user;
    }

    @Transactional
    public void syncUser(@NotNull KeycloakUserDto dto) {
        if (dto.id() == null) return;

        User user = userRepository.findById(dto.id()).orElseGet(() -> {
            User u = new User();
            u.setId(dto.id());
            return u;
        });

        boolean changed = false;

        if (!user.isInKeycloak()) {
            user.setInKeycloak(true);
            changed = true;
        }

        if (!Objects.equals(user.getUsername(), dto.username())) {
            user.setUsername(dto.username());
            changed = true;
        }

        if (!Objects.equals(user.getFirstName(), dto.firstName())) {
            user.setFirstName(dto.firstName());
            changed = true;
        }

        if (!Objects.equals(user.getLastName(), dto.lastName())) {
            user.setLastName(dto.lastName());
            changed = true;
        }

        if (!Objects.equals(user.getEmail(), dto.email())) {
            user.setEmail(dto.email());
            changed = true;
        }

        // Sync Roles
        Set<Role> localRoles = mapRoles(dto.roles());
        if (!user.getRoles().equals(localRoles)) {
            user.setRoles(localRoles);
            changed = true;
        }

        if (changed || user.isNew()) {
            userRepository.save(user);
        }
    }

    @Transactional
    public void markMissingUsers(Collection<UUID> currentIds) {
        if (currentIds.isEmpty()) return;
        userRepository.markMissingUsers(currentIds);
    }

    private Set<Role> mapRoles(Collection<String> roleNames) {
        Set<Role> localRoles = new HashSet<>();
        if (roleNames != null) {
            for (String rName : roleNames) {
                roleRepository.findByNameIgnoreCase(rName)
                        .ifPresent(localRoles::add);
            }
        }

        if (localRoles.isEmpty()) {
            roleRepository.findByNameIgnoreCase("Guest").ifPresent(localRoles::add);
        }
        return localRoles;
    }
    
    @SuppressWarnings("unchecked")
    @NotNull
    public Set<String> extractRolesFromJwt(@NotNull Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            roles.addAll((List<String>) realmAccess.get("roles"));
        }
        // Also check resource_access if needed, but realm_access is standard for realm roles
        return roles;
    }

    @Transactional
    @NotNull
    public User updateUserAttributes(@NotNull UUID id, @Nullable Integer rank, @Nullable String description, @Nullable String displayName, @Nullable Long version, @Nullable java.time.LocalDate joinDate) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found"));

        if (version != null && user.getVersion() != null && !user.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(User.class, id);
        }

        if (rank != null) {
            boolean isOfficer = user.getRoles().stream()
                    .anyMatch(r -> r.getName().equalsIgnoreCase("OFFICER"));
            boolean isSquadronMember = user.getRoles().stream()
                    .anyMatch(r -> r.getName().equalsIgnoreCase("SQUADRON_MEMBER"));

            if (isOfficer) {
                if (rank < 1 || rank > 12) {
                    throw new IllegalArgumentException("Officers can only have rank 1-12");
                }
            } else if (isSquadronMember) {
                if (rank < 13 || rank > 20) {
                    throw new IllegalArgumentException("Squadron members can only have rank 13-20");
                }
            }
            user.setRank(rank);
        }
        if (description != null) user.setDescription(description);
        if (displayName != null) user.setDisplayName(displayName.isBlank() ? null : displayName);
        // joinDate can be explicitly set to null (clear the date)
        user.setJoinDate(joinDate);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserDescription(@NotNull UUID id, @Nullable String description, @Nullable String displayName, @Nullable Long version) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found"));
        if (version != null && user.getVersion() != null && !user.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(User.class, id);
        }
        if (description != null) user.setDescription(description);
        if (displayName != null) user.setDisplayName(displayName.isBlank() ? null : displayName);
        return userRepository.save(user);
    }

    @Transactional
    public User updateReadAnnouncement(@NotNull UUID id, @NotNull UUID announcementId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found"));
        user.setLastReadAnnouncementId(announcementId);
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll(Sort.by(Sort.Order.asc("username").ignoreCase()));
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto> findAllReference() {
        return userRepository.findAllReference();
    }

    public Page<User> findAll(@NotNull Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public List<User> searchByUsername(@NotNull String query) {
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(query, query);
    }

    public Page<User> searchByUsername(@NotNull String query, @NotNull Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(query, query, pageable);
    }

    public User findById(@NotNull UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found"));
    }

    public Optional<User> getCurrentUser() {
        Authentication auth = authHelperService.rawAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        UUID userId = getUserIdFromJwt(jwt);
        return userRepository.findById(userId);
    }

    @Transactional
    public User updateLogisticianStatus(UUID userId, boolean isLogistician) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
        user.setLogistician(isLogistician);
        return userRepository.save(user);
    }

    @Transactional
    public User updateMissionManagerStatus(UUID userId, boolean isMissionManager) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
        user.setMissionManager(isMissionManager);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (user.isInKeycloak()) {
            throw new IllegalStateException("Cannot delete user that is still in Keycloak");
        }

        User admin = userRepository.findAllAdmins().stream()
                .filter(u -> !u.getId().equals(userId))
                .findFirst()
                .orElseGet(() -> getCurrentUser()
                        .filter(u -> !u.getId().equals(userId))
                        .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("ADMIN")))
                        .orElseThrow(() -> new IllegalStateException("No admin user found to reassign data")));

        // Reassign mandatory fields
        inventoryItemRepository.updateOwner(user, admin);
        shipRepository.updateOwner(user, admin);
        refineryOrderRepository.updateOwner(user, admin);
        missionRepository.updateOwner(user, admin);

        // Remove ManyToMany and nullable references
        missionRepository.removeManager(userId);
        jobOrderRepository.removeAssignee(userId);
        missionParticipantRepository.unlinkUser(userId);

        // Delete the user
        userRepository.delete(user);
        log.info("User {} deleted and references reassigned to admin {}", userId, admin.getId());
    }
}
