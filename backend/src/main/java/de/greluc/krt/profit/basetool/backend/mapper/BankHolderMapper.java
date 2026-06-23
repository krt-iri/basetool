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

package de.greluc.krt.profit.basetool.backend.mapper;

import de.greluc.krt.profit.basetool.backend.model.BankHolder;
import de.greluc.krt.profit.basetool.backend.model.dto.BankHolderDto;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper from {@link BankHolder} to its response DTO. The custody totals are grouped
 * ledger sums (not entity state), so they travel as extra source parameters paired in by the
 * service.
 */
@Mapper(componentModel = "spring")
public interface BankHolderMapper {

  /**
   * Maps one holder row plus its externally computed global custody total to the response DTO. The
   * {@code userId} resolves through the lazy {@code user} proxy without initialising it (id-only
   * access); {@code roleManaged} comes straight off the entity.
   *
   * @param holder the holder entity
   * @param totalHeld signed global sum the holder physically holds across the whole bank
   * @return the response DTO
   */
  @Mapping(target = "id", source = "holder.id")
  @Mapping(target = "userId", source = "holder.user.id")
  @Mapping(target = "handle", source = "holder.handle")
  @Mapping(target = "active", source = "holder.active")
  @Mapping(target = "roleManaged", source = "holder.roleManaged")
  @Mapping(target = "version", source = "holder.version")
  @Mapping(target = "totalHeld", source = "totalHeld")
  BankHolderDto toDto(BankHolder holder, BigDecimal totalHeld);
}
