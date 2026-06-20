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

package de.greluc.krt.profit.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

/** User JPA entity. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class User extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  private UUID id;

  @Override
  public boolean isNew() {
    return getVersion() == null;
  }

  private String username;
  private String displayName;
  private String email;

  @Min(1)
  @Max(20)
  @Column(name = "user_rank")
  private Integer rank;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "last_read_announcement_id")
  private UUID lastReadAnnouncementId;

  @Column(name = "in_keycloak")
  private boolean inKeycloak = true;

  @Nullable
  @Column(name = "join_date")
  private LocalDate joinDate;

  /**
   * The user's personal default payout preference. Pre-fills the per-participant {@code
   * payoutPreference} when this user signs up to a mission (see {@link
   * de.greluc.krt.profit.basetool.backend.service.MissionService#addParticipant}). {@code null}
   * means the user has expressed no explicit choice, in which case sign-up falls back to {@link
   * PayoutPreference#PAYOUT}; the value is never auto-populated. Editing it is a forward-only
   * default — it does not rewrite existing {@link MissionParticipant} rows. REQ-MISSION-002.
   */
  @Nullable
  @Enumerated(EnumType.STRING)
  @Column(name = "default_payout_preference")
  private PayoutPreference defaultPayoutPreference;

  /**
   * Opt-in flag: when {@code true}, the user's owned {@link PersonalBlueprint} rows are counted in
   * the leadership blueprint-availability overview and the item-order blueprint-coverage view for
   * <em>every</em> org unit, not only the ones the user is a member of — so a Staffel member's
   * blueprint can satisfy an SK order's coverage even across org-unit boundaries. Defaults to
   * {@code false}, preserving the strict org-unit scoping for everyone who does not opt in. The
   * widening is read-only and exposes the owner by display name only (never the {@code sub} or
   * e-mail); the viewer-access gates are unchanged. REQ-INV-018 / ADR-0024.
   */
  @Column(name = "share_blueprints_globally", nullable = false)
  private boolean shareBlueprintsGlobally = false;

  /**
   * The user's linked Discord account id (a numeric snowflake, stored as text). Written by the
   * Keycloak Discord identity-provider mapper into the {@code discord_user_id} token claim and
   * persisted here on login, so a returning Discord user is recognised. {@code null} for users who
   * only ever signed in with credentials; at most one {@link User} per Discord id (DB-unique). This
   * column merely records the federation link — the guild + KRT-Mitglied membership gate itself
   * lives in the Keycloak SPI, never here. Epic #720, Track 1 / REQ-DATA-006.
   */
  @Nullable
  @Column(name = "discord_user_id", unique = true)
  private String discordUserId;

  /**
   * Account approval lifecycle (epic #720, Track 1, REQ-SEC-017). A brand-new Discord registration
   * is {@link ApprovalStatus#PENDING} (no authorities granted — only {@code ROLE_PENDING_APPROVAL})
   * until an admin approves; credential/admin-created and all pre-existing users are {@link
   * ApprovalStatus#ACTIVE}. Defaults to {@code ACTIVE} so any non-Discord creation path is active
   * unless {@link UserService#syncUser(org.springframework.security.oauth2.jwt.Jwt)} explicitly
   * lands a new Discord login in {@code PENDING}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false)
  private ApprovalStatus approvalStatus = ApprovalStatus.ACTIVE;

  /** When the registration was approved/rejected; {@code null} while still {@code PENDING}. */
  @Nullable
  @Column(name = "approved_at")
  private Instant approvedAt;

  /**
   * The admin who approved/rejected this registration; {@code null} while still {@code PENDING}.
   */
  @Nullable
  @Column(name = "approved_by_id")
  private UUID approvedById;

  /**
   * Whether the account may be granted its full authorities. {@code true} only for {@link
   * ApprovalStatus#ACTIVE}; {@code PENDING} and {@code REJECTED} accounts receive no authorities.
   *
   * @return {@code true} iff the approval status is {@link ApprovalStatus#ACTIVE}
   */
  public boolean isApproved() {
    return approvalStatus == ApprovalStatus.ACTIVE;
  }

  public String getEffectiveName() {
    return (displayName != null && !displayName.isBlank()) ? displayName : username;
  }

  // @ToString.Exclude on the LAZY @ManyToMany so a logged User outside of a
  // Hibernate session does not trigger LazyInitializationException — and so
  // toString() does not recurse User -> Role.permissions -> ... when Role
  // proxies are subsequently hydrated.
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @ToString.Exclude
  private Set<Role> roles = new HashSet<>();
}
