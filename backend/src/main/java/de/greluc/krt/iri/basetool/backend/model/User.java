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

package de.greluc.krt.iri.basetool.backend.model;

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
   * de.greluc.krt.iri.basetool.backend.service.MissionService#addParticipant}). {@code null} means
   * the user has expressed no explicit choice, in which case sign-up falls back to {@link
   * PayoutPreference#PAYOUT}; the value is never auto-populated. Editing it is a forward-only
   * default — it does not rewrite existing {@link MissionParticipant} rows. REQ-MISSION-002.
   */
  @Nullable
  @Enumerated(EnumType.STRING)
  @Column(name = "default_payout_preference")
  private PayoutPreference defaultPayoutPreference;

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
