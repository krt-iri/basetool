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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.backend.service.OrgUnitMembershipService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Thin delegation tests for {@link OrgUnitController}. The endpoint is a single-line passthrough to
 * {@link OrgUnitMembershipService#listAllActiveOptions} — the resolver / sort behaviour is pinned
 * by {@link de.greluc.krt.iri.basetool.backend.service.OrgUnitMembershipServiceTest}, so this class
 * only verifies the wiring (handler exists, response shape preserved, no surprise filtering).
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitControllerTest {

  @Mock private OrgUnitMembershipService orgUnitMembershipService;

  @InjectMocks private OrgUnitController controller;

  @Test
  void listActiveOrgUnits_delegatesToService() {
    OrgUnitMembershipOptionDto option =
        new OrgUnitMembershipOptionDto(UUID.randomUUID(), "IRIDIUM", "IRI", OrgUnitKind.SQUADRON);
    when(orgUnitMembershipService.listAllActiveOptions()).thenReturn(List.of(option));

    List<OrgUnitMembershipOptionDto> result = controller.listActiveOrgUnits();

    assertEquals(1, result.size());
    assertSame(option, result.getFirst());
    verify(orgUnitMembershipService).listAllActiveOptions();
  }

  @Test
  void listActiveOrgUnits_emptyService_returnsEmptyList() {
    when(orgUnitMembershipService.listAllActiveOptions()).thenReturn(List.of());

    assertTrue(controller.listActiveOrgUnits().isEmpty());
  }
}
