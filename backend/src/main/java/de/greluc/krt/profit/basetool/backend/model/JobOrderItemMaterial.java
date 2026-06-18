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
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One material requirement of a single {@link JobOrderItem} line, snapshotted from the chosen
 * blueprint at order-creation time so the order stays stable even if the wiki/blueprint data later
 * changes. {@link #requiredQuantity} is a raw number whose unit is interpreted from the linked
 * {@link Material#getQuantityType()} (SCU fractional vs PIECE whole-number) — the unit is never
 * stored here. {@link #qualityRequirement} captures the requester's per-order Gut/Keine choice.
 * Aggregation for the detail view groups these rows by {@code (material, qualityRequirement)}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order_item_material")
public class JobOrderItemMaterial extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Owning ordered-item line. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_item_id", nullable = false)
  private JobOrderItem jobOrderItem;

  /** The material required to produce the owning line's item. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  /**
   * Required amount snapshotted from the blueprint ingredient, scaled by the line's quantity. The
   * unit is governed by {@link Material#getQuantityType()}: for a PIECE material this is a
   * whole-unit count, for an SCU material a fractional SCU value.
   */
  @Column(name = "required_quantity", nullable = false)
  private Double requiredQuantity;

  /** Whether this material is needed in Gut (700+) or Keine quality for this order. */
  @Enumerated(EnumType.STRING)
  @Column(name = "quality_requirement", nullable = false, length = 8)
  private QualityRequirement qualityRequirement;
}
