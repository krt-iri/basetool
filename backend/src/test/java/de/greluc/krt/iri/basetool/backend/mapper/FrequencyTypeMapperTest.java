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

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class FrequencyTypeMapperTest {

  private final FrequencyTypeMapper mapper = Mappers.getMapper(FrequencyTypeMapper.class);

  @Test
  void toDto_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    FrequencyType entity = new FrequencyType();
    entity.setId(id);
    entity.setName("Combat");
    entity.setDescription("Combat radio frequency");
    entity.setActive(true);
    entity.setSortIndex(5);
    entity.setVersion(4L);

    // When
    FrequencyTypeDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Combat", dto.name());
    assertEquals("Combat radio frequency", dto.description());
    assertTrue(dto.active());
    assertEquals(5, dto.sortIndex());
    assertEquals(4L, dto.version());
  }

  @Test
  void toEntity_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    FrequencyTypeDto dto = new FrequencyTypeDto(id, "Recon", "Recon channel", false, 12, 2L);

    // When
    FrequencyType entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Recon", entity.getName());
    assertEquals("Recon channel", entity.getDescription());
    assertFalse(entity.isActive());
    assertEquals(12, entity.getSortIndex());
    assertEquals(2L, entity.getVersion());
  }

  @Test
  void roundtrip_shouldPreserveAllFields() {
    // Given
    FrequencyTypeDto original =
        new FrequencyTypeDto(
            UUID.randomUUID(), "Logistics", "Cargo and supply chatter", true, 1, 7L);

    // When
    FrequencyTypeDto back = mapper.toDto(mapper.toEntity(original));

    // Then
    assertEquals(original, back);
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
