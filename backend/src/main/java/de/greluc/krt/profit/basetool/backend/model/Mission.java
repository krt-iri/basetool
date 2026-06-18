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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OptimisticLock;

/** Mission JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"participants", "assignedUnits", "subMissions", "financeEntries"})
public class Mission extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 2048)
  private String calendarLink;

  private String status; // e.g., PLANNED, ACTIVE, COMPLETED

  private Instant meetingTime;
  private Instant plannedStartTime;
  private Instant actualStartTime;
  private Instant plannedEndTime;
  private Instant actualEndTime;

  @Column(name = "is_internal", nullable = false)
  private Boolean isInternal = false;

  /**
   * Section-scoped optimistic-lock counter for the {@code core} patch endpoint (name, description,
   * calendar link, status, operation). Independent of {@link AbstractEntity#getVersion()} so that
   * concurrent edits on {@code schedule} and {@code flags} do not produce spurious 409 conflicts.
   * Marked {@code @OptimisticLock(excluded = true)} so bumping it does not in turn bump the global
   * {@link AbstractEntity#getVersion()}.
   */
  @Column(name = "core_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long coreVersion = 0L;

  /**
   * Section-scoped optimistic-lock counter for the {@code schedule} patch endpoint (meeting,
   * planned-start, planned-end, actual-start, actual-end). Status-driven auto-transitions that set
   * {@code actualStartTime} (PLANNED → ACTIVE) bump this counter via {@code …WithinTransaction}.
   */
  @Column(name = "schedule_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long scheduleVersion = 0L;

  /**
   * Section-scoped optimistic-lock counter for the {@code flags} patch endpoint ({@code
   * isInternal}).
   */
  @Column(name = "flags_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long flagsVersion = 0L;

  /**
   * Section-scoped optimistic-lock counter for the {@code party-lead} endpoint ({@link
   * #partyLeadUser} / {@link #partyLeadGuestName}). Independent of the global {@link
   * AbstractEntity#getVersion()} and marked {@code @OptimisticLock(excluded = true)} so assigning a
   * party lead does not invalidate other users' open forms on the same mission.
   */
  @Column(name = "party_lead_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long partyLeadVersion = 0L;

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionParticipant> participants = new HashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("name ASC")
  @OptimisticLock(excluded = true)
  private Set<MissionUnit> assignedUnits = new LinkedHashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionFrequency> frequencies = new HashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionFinanceEntry> financeEntries = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_mission_id")
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Mission parent;

  @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
  @OptimisticLock(excluded = true)
  private Set<Mission> subMissions = new HashSet<>();

  @OneToMany(mappedBy = "mission")
  @OrderBy("createdAt DESC")
  @com.fasterxml.jackson.annotation.JsonIgnore
  @OptimisticLock(excluded = true)
  private Set<InventoryItem> inventoryEntries = new LinkedHashSet<>();

  @OneToMany(mappedBy = "mission")
  @OrderBy("startedAt DESC")
  @com.fasterxml.jackson.annotation.JsonIgnore
  @OptimisticLock(excluded = true)
  private Set<RefineryOrder> refineryOrders = new LinkedHashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "operation_id")
  @OptimisticLock(excluded = true)
  private Operation operation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  @OptimisticLock(excluded = true)
  private User owner;

  /**
   * Optional party lead (Partyleiter) of this mission, as a linked registered user. Mutually
   * exclusive with {@link #partyLeadGuestName}: a registered party lead clears the guest handle and
   * vice versa. {@code null} when no party lead is assigned or when the lead is an unregistered
   * person captured via {@link #partyLeadGuestName}. Excluded from the global optimistic-lock; the
   * dedicated {@link #partyLeadVersion} guards concurrent edits instead.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "party_lead_user_id")
  @OptimisticLock(excluded = true)
  private User partyLeadUser;

  /**
   * Optional free-text party-lead handle for an unregistered/anonymous person, mirroring {@link
   * MissionParticipant#getGuestName()}. Mutually exclusive with {@link #partyLeadUser}. {@code
   * null} when no party lead is assigned or when the lead is a registered user.
   */
  @Column(name = "party_lead_guest_name", length = 100)
  @OptimisticLock(excluded = true)
  private String partyLeadGuestName;

  @ManyToMany
  @JoinTable(
      name = "mission_managers",
      joinColumns = @JoinColumn(name = "mission_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @OptimisticLock(excluded = true)
  private Set<User> managers = new HashSet<>();

  /**
   * Org-unit owner of this mission, or {@code null} for an <em>ownerless leadership mission</em>.
   * Set at creation time from the caller's active org-unit context (via {@code
   * OwnerScopeService.resolveOrgUnitForPickerOutputNullable}) and immutable afterwards. Gates
   * read/write access together with {@link #isInternal}:
   *
   * <ul>
   *   <li><b>Org-owned</b> (non-null): non-internal missions are visible across org units, internal
   *       ones are restricted to the owning org unit and admins.
   *   <li><b>Ownerless</b> (null): created by a user who belongs to no OrgUnit but is allowed to
   *       plan org-wide missions (organisation leadership / "Bereichsleitung", which sits above
   *       every Staffel and SK). Such a mission is attributable through its {@link #owner}. A
   *       non-internal ownerless mission is visible to everyone (the public default); an internal
   *       one is visible to organisation members-or-above. Editing follows the usual
   *       mission-management gate (elevated roles, owner, co-managers, admins), minus the
   *       squadron-scope narrowing. See {@code OwnerScopeService.canSeeMission} / {@code
   *       canEditMission}.
   * </ul>
   *
   * <p>R9 Step 2 dropped the legacy {@code owningSquadron} mirror field together with the
   * {@code @PrePersist} / {@code @PreUpdate} / {@code @PostLoad} {@code syncOwnerFields()}
   * lifecycle hook; V100 drops the matching {@code owning_squadron_id} column. V99 first tightened
   * the new column to NOT NULL; V144 relaxed it again so an ownerless mission can persist
   * (mirroring the V132 relaxation for ship / refinery order / inventory item).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id")
  @OptimisticLock(excluded = true)
  private OrgUnit owningOrgUnit;
}
