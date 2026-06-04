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

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.dto.ManufacturerDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ManufacturerMapperTest {

  private final ManufacturerMapper mapper = Mappers.getMapper(ManufacturerMapper.class);

  @Test
  void toDto_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    Manufacturer entity = new Manufacturer();
    entity.setId(id);
    entity.setName("Roberts Space Industries");
    entity.setAbbreviation("RSI");
    entity.setNickname("Roberts");
    entity.setWiki("https://wiki.example.com/rsi");
    entity.setDescription("Founding manufacturer");
    entity.setHidden(true);

    // When
    ManufacturerDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Roberts Space Industries", dto.name());
    assertEquals("RSI", dto.abbreviation());
    assertEquals("Roberts", dto.nickname());
    assertEquals("https://wiki.example.com/rsi", dto.wiki());
    assertEquals("Founding manufacturer", dto.description());
    assertTrue(dto.hidden());
  }

  @Test
  void toEntity_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    ManufacturerDto dto = new ManufacturerDto(id, "Anvil", "ANVL", null, null, null, false);

    // When
    Manufacturer entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Anvil", entity.getName());
    assertEquals("ANVL", entity.getAbbreviation());
    assertNull(entity.getNickname());
    assertNull(entity.getWiki());
    assertNull(entity.getDescription());
    assertFalse(entity.isHidden());
  }

  @Test
  void roundtrip_shouldPreserveAllFields() {
    // Given
    ManufacturerDto dto =
        new ManufacturerDto(
            UUID.randomUUID(),
            "Aegis Dynamics",
            "AEGS",
            "Aegis",
            "https://example.org/aegis",
            "Military focus",
            true);

    // When
    Manufacturer entity = mapper.toEntity(dto);
    ManufacturerDto roundtrip = mapper.toDto(entity);

    // Then
    assertEquals(dto, roundtrip);
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
