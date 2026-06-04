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

package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity → DTO mapper for {@link PersonalBlueprint}.
 *
 * <p>The {@code ownerSub} is never copied into the response — it is the internal isolation key and
 * must not leak to clients (see the multi-user data isolation rule). The optional output-item
 * association is flattened to its id; writes are applied field-by-field in the service (only {@code
 * acquiredAt} / {@code note} are mutable), so no entity-write mapping is needed here.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PersonalBlueprintMapper {

  /**
   * Maps an owned blueprint to its response DTO, flattening the optional {@code outputItem}
   * association to {@code outputItemId} ({@code null} when unresolved).
   *
   * @param entity the owned blueprint
   * @return the response DTO
   */
  @Mapping(target = "outputItemId", source = "outputItem.id")
  PersonalBlueprintResponse toResponse(PersonalBlueprint entity);
}
