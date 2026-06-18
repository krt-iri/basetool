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
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Composite primary key for {@link OrgUnitMembership}: the pair {@code (user_id, org_unit_id)}.
 * Matches the composite PK declared on the {@code org_unit_membership} table in Flyway migration
 * V95.
 *
 * <p>This is the first composite-key entity in the project — every existing aggregate uses a single
 * UUID primary key. The pair-based PK is intentional here because a membership has no standalone
 * identity outside the {@code (user, org_unit)} relationship: re-adding a user to an org unit they
 * previously left should re-use the same row, and the partial unique index in V95 (one Staffel per
 * user) leans on the pair being the key, not an arbitrary surrogate.
 *
 * <p>JPA requires {@link Embeddable} composite IDs to implement {@link Serializable} and to provide
 * value-based {@link #equals(Object)} / {@link #hashCode()} (so the second-level cache and
 * persistence-context lookups work correctly when the entity is detached and re-attached). Lombok's
 * default {@code equals}/{@code hashCode} would use class identity rather than field values, so the
 * methods are hand-written below.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrgUnitMembershipId implements Serializable {

  /**
   * Serial version UID for the {@link Serializable} contract. Held constant at {@code 1L} because
   * the field layout of this composite key is immutable — any future schema change to {@code
   * org_unit_membership}'s PK shape needs a new entity rather than a back-compat deserialisation
   * path.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Foreign-key half of the composite: identifies the user holding the membership. Always matches
   * the {@code @ManyToOne user} reference on the enclosing {@link OrgUnitMembership} entity at read
   * time; the value is set explicitly by the service layer on insert (no {@code @MapsId}
   * indirection, see the rationale on {@link OrgUnitMembership}).
   */
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /**
   * Foreign-key half of the composite: identifies the org unit the membership belongs to. Stored as
   * a plain UUID rather than a JPA relation to {@link OrgUnit} so the membership row can reference
   * a {@code kind = 'SQUADRON'} row that has no Java subclass during the R2.a soak window — see the
   * class-level Javadoc on {@link OrgUnit} for why Squadron is not in the JPA inheritance hierarchy
   * yet.
   */
  @Column(name = "org_unit_id", nullable = false)
  private UUID orgUnitId;

  /**
   * Value-based equality on the two UUID fields. Required by the JPA contract for composite keys —
   * without it, persistence-context lookups for a detached membership re-attach would miss the
   * cache and Hibernate would issue redundant SELECTs.
   *
   * @param other the object to compare; may be {@code null}.
   * @return {@code true} iff {@code other} is an {@link OrgUnitMembershipId} carrying the same
   *     {@code userId} and {@code orgUnitId}.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof OrgUnitMembershipId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId) && Objects.equals(orgUnitId, that.orgUnitId);
  }

  /**
   * Hash code derived from the two UUID fields, paired with {@link #equals(Object)} per the
   * standard contract. Required so the composite key works as a {@code HashMap} key in Hibernate's
   * persistence-context lookups.
   *
   * @return hash consistent with {@link #equals(Object)}.
   */
  @Override
  public int hashCode() {
    return Objects.hash(userId, orgUnitId);
  }
}
