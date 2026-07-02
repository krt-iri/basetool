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

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import de.greluc.krt.profit.basetool.backend.event.DiscordRegistrationPendingEvent;
import de.greluc.krt.profit.basetool.backend.model.ApprovalStatus;
import de.greluc.krt.profit.basetool.backend.model.Role;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.RoleRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for the Discord-approval branch of {@link UserService#syncUser(Jwt)} (PR review #5): a
 * new Discord federated login becomes PENDING and notifies admins; an admin is never left PENDING;
 * a credential login is unaffected.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDiscordSyncTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private DefaultBlueprintProvisioningService defaultBlueprintProvisioningService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private UserService userService;

  private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  private static final String DISCORD_ID = "123456789012345678";

  private static Jwt jwt(boolean withDiscord, List<String> realmRoles) {
    Jwt.Builder builder =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(USER_ID.toString())
            .claim("preferred_username", "discorduser")
            .claim("realm_access", Map.of("roles", realmRoles));
    if (withDiscord) {
      builder.claim("discord_user_id", DISCORD_ID);
    }
    return builder.build();
  }

  private static Role role(String code, String name) {
    Role role = new Role();
    role.setCode(code);
    role.setName(name);
    return role;
  }

  @Test
  void newDiscordNonAdmin_landsPending_andNotifiesAdmins() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(true, List.of()));

    assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
    assertEquals(DISCORD_ID, result.getDiscordUserId());
    verify(eventPublisher).publishEvent(any(DiscordRegistrationPendingEvent.class));
  }

  @Test
  void newDiscordAdmin_landsActive_noNotification() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Admin"))
        .thenReturn(Optional.of(role("ADMIN", "Admin")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(true, List.of("Admin")));

    assertEquals(ApprovalStatus.ACTIVE, result.getApprovalStatus());
    verify(eventPublisher, never()).publishEvent(any());
  }

  /**
   * Security hardening (PR #740 review): a new Discord login MUST NOT be matched onto a
   * pre-existing row by {@code preferred_username}. The brokered Discord username is
   * attacker-influenced, so the legacy username fallback is suppressed for a Discord login —
   * otherwise a verified guild member could link their Discord identity to someone else's (possibly
   * privileged, already-ACTIVE) account and bypass the PENDING gate. The Discord identity is a
   * brand-new PENDING registration keyed by its own subject, and {@code findByUsername} is never
   * consulted.
   */
  @Test
  void newDiscordLogin_ignoresMatchingCredentialUsername_landsPending() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(true, List.of()));

    assertEquals(USER_ID, result.getId());
    assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
    assertEquals(DISCORD_ID, result.getDiscordUserId());
    // The core guarantee: a Discord login is recognised only by subject, never by username.
    verify(userRepository, never()).findByUsername(any());
    verify(eventPublisher).publishEvent(any(DiscordRegistrationPendingEvent.class));
  }

  @Test
  void newPendingRegistration_notifiesAdmins_evenWithoutDiscordClaim() {
    // REQ-NOTIF-012 regression guard: the admin notification is keyed off the PENDING transition,
    // NOT off the discord_user_id claim. A brand-new non-admin registration whose token carries NO
    // discord_user_id claim (jwt(false, ...)) — e.g. because the optional Keycloak claim mapper is
    // absent/misconfigured — still lands PENDING (fail-safe, REQ-SEC-017) AND still notifies every
    // admin. This is the exact fragility that previously silenced all approval notifications when
    // the mapper was not wired: PENDING was decoupled from the claim but the notification was not.
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userRepository.findByUsername("discorduser")).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(false, List.of()));

    assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
    assertNull(result.getDiscordUserId());
    verify(eventPublisher).publishEvent(any(DiscordRegistrationPendingEvent.class));
  }

  @Test
  void newCredentialAdmin_landsActive_noNotification() {
    // ADMIN bootstrap carve-out: a brand-new Keycloak ADMIN-realm-role holder is ACTIVE even
    // without
    // Discord, so the first admin can never be locked out by the fail-safe default.
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userRepository.findByUsername("discorduser")).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Admin"))
        .thenReturn(Optional.of(role("ADMIN", "Admin")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(false, List.of("Admin")));

    assertEquals(ApprovalStatus.ACTIVE, result.getApprovalStatus());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void existingPendingAdmin_isPromotedToActive() {
    User existing = new User();
    existing.setId(USER_ID);
    existing.setUsername("discorduser");
    existing.setApprovalStatus(ApprovalStatus.PENDING);
    existing.setVersion(1L);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(roleRepository.findByNameIgnoreCase("Admin"))
        .thenReturn(Optional.of(role("ADMIN", "Admin")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(false, List.of("Admin")));

    assertEquals(ApprovalStatus.ACTIVE, result.getApprovalStatus());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void newNonAdmin_landsActive_whenApprovalNotRequired() {
    // With app.registration.require-approval=false (the e2e stack), a brand-new non-admin is
    // created
    // ACTIVE rather than PENDING, so on-the-fly fixture seeding is never blocked by the approval
    // gate
    // on an ephemeral DB that has no V173 ACTIVE backfill.
    setField(userService, "requireApproval", false);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userRepository.findByUsername("discorduser")).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(false, List.of()));

    assertEquals(ApprovalStatus.ACTIVE, result.getApprovalStatus());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void newDiscordLogin_persistsGuildNickname_trimmed() {
    // covers REQ-DATA-008 — the per-guild server nickname claim is persisted (trimmed) for display
    // in the admin approval queue.
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(USER_ID.toString())
            .claim("preferred_username", "discorduser")
            .claim("realm_access", Map.of("roles", List.of()))
            .claim("discord_user_id", DISCORD_ID)
            .claim("discord_guild_nickname", "  Vanguard Pilot  ")
            .build();

    User result = userService.syncUser(jwt);

    assertEquals("Vanguard Pilot", result.getDiscordGuildNickname());
  }

  @Test
  void discordLoginWithoutNicknameClaim_leavesGuildNicknameNull() {
    // covers REQ-DATA-008 — the nickname capture is best-effort/optional: an absent claim leaves
    // the
    // field null rather than failing the login.
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(roleRepository.findByNameIgnoreCase("Guest"))
        .thenReturn(Optional.of(role("GUEST", "Guest")));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.syncUser(jwt(true, List.of()));

    assertNull(result.getDiscordGuildNickname());
  }
}
