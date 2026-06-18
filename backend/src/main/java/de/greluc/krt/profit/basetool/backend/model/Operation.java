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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Operation JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"missions"})
public class Operation extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OperationStatus status;

  @OneToMany(mappedBy = "operation")
  @OrderBy("plannedStartTime DESC")
  private Set<Mission> missions = new HashSet<>();

  /**
   * Org-unit owner of this operation, or {@code null} for an <em>ownerless leadership
   * operation</em>. After R9 Step 2 dropped the legacy {@code owningSquadron} mirror field together
   * with the {@code syncOwnerFields()} lifecycle hook, callers stamp this field directly via {@code
   * OwnerScopeService.resolveOrgUnitForPickerOutputNullable}; V100 drops the matching {@code
   * owning_squadron_id} column.
   *
   * <p>{@code null} is a legitimate value since V145 (#500): organisation leadership
   * ("Bereichsleitung") belongs to no OrgUnit but may plan org-wide operations, so a membershipless
   * creator yields a {@code null} owner instead of a 400. Unlike {@code Mission}, an operation has
   * no per-user owner column, so an ownerless operation is attributable only as an
   * organisation-wide leadership operation; and operations carry no {@code is_internal} flag, so an
   * ownerless operation is visible to organisation members-or-above only (never anonymous). See
   * REQ-ORG-009 and {@code OwnerScopeService.canSeeOperation}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id")
  private OrgUnit owningOrgUnit;
}
