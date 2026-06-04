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

package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintProductDto;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for {@link BlueprintProductController}. */
@ExtendWith(MockitoExtension.class)
class BlueprintProductControllerTest {

  private static final String SUB = "user-sub-1";

  @Mock private BlueprintProductService service;
  @InjectMocks private BlueprintProductController controller;

  private JwtAuthenticationToken auth;

  @BeforeEach
  void setUp() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", SUB).build();
    auth = new JwtAuthenticationToken(jwt);
  }

  @Test
  void search_derivesSubAndAppliesDefaultLimit() {
    BlueprintProductDto dto = new BlueprintProductDto("k", "Name", 1, null, "BP", false);
    when(service.searchProducts(eq("pi"), eq(BlueprintProductService.DEFAULT_LIMIT), eq(SUB)))
        .thenReturn(List.of(dto));

    List<BlueprintProductDto> result = controller.search("pi", null, auth);

    assertEquals(1, result.size());
    verify(service).searchProducts("pi", BlueprintProductService.DEFAULT_LIMIT, SUB);
  }

  @Test
  void search_relaysExplicitLimit() {
    when(service.searchProducts(any(), eq(5), eq(SUB))).thenReturn(List.of());

    controller.search("x", 5, auth);

    verify(service).searchProducts("x", 5, SUB);
  }

  @Test
  void search_rejectsMissingJwtWithAccessDenied() {
    assertThrows(AccessDeniedException.class, () -> controller.search("x", null, null));
  }
}
