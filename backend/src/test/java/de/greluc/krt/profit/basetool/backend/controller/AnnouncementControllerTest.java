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

package de.greluc.krt.profit.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.profit.basetool.backend.mapper.AnnouncementMapper;
import de.greluc.krt.profit.basetool.backend.model.Announcement;
import de.greluc.krt.profit.basetool.backend.model.dto.AnnouncementDto;
import de.greluc.krt.profit.basetool.backend.service.AnnouncementService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pure-method unit tests for {@link AnnouncementController}. There is a separate {@code
 * AnnouncementControllerTest} (Spring-Boot integration suite) elsewhere; this file focuses on the
 * controller's internal logic in isolation — the {@code getPublicAnnouncement} branch
 * (Optional.empty → 204 No Content) is the only non-trivial code path.
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {

  @Mock private AnnouncementService service;
  @Mock private AnnouncementMapper mapper;

  @InjectMocks private AnnouncementController controller;

  // ── getPublicAnnouncement ───────────────────────────────────────────────

  @Test
  void getPublicAnnouncement_whenServiceHasOne_returns200WithMappedBody() {
    Announcement entity = new Announcement();
    AnnouncementDto dto =
        new AnnouncementDto(UUID.randomUUID(), "Server maintenance", Instant.now(), 1L);
    when(service.getPublicAnnouncement()).thenReturn(Optional.of(entity));
    when(mapper.toDto(entity)).thenReturn(dto);

    ResponseEntity<AnnouncementDto> result = controller.getPublicAnnouncement();

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertSame(dto, result.getBody());
  }

  @Test
  void getPublicAnnouncement_whenNoneExists_returns204WithEmptyBody() {
    // Given — service returns Optional.empty, controller must NOT return
    // a fake DTO; the 204 contract is documented in the frontend
    // ErrorHandler (it specifically suppresses logging on 204).
    when(service.getPublicAnnouncement()).thenReturn(Optional.empty());

    ResponseEntity<AnnouncementDto> result = controller.getPublicAnnouncement();

    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    assertNull(result.getBody(), "204 No Content must not carry a JSON payload");
  }

  // ── getAdminAnnouncement ────────────────────────────────────────────────

  @Test
  void getAdminAnnouncement_returnsMappedDto() {
    // The admin endpoint always returns a DTO (the service creates the
    // singleton row if missing) — no 204 branch here.
    Announcement entity = new Announcement();
    AnnouncementDto dto = new AnnouncementDto(UUID.randomUUID(), "", Instant.now(), 1L);
    when(service.getAdminAnnouncement()).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    AnnouncementDto result = controller.getAdminAnnouncement();

    assertSame(dto, result);
  }

  // ── updateAnnouncement ──────────────────────────────────────────────────

  @Test
  void updateAnnouncement_forwardsContentAndVersionToService() {
    AnnouncementController.AnnouncementRequest req =
        new AnnouncementController.AnnouncementRequest();
    req.setContent("New content");
    req.setVersion(3L);

    Announcement updated = new Announcement();
    AnnouncementDto dto = new AnnouncementDto(UUID.randomUUID(), "New content", Instant.now(), 4L);
    when(service.updateAnnouncement("New content", 3L)).thenReturn(updated);
    when(mapper.toDto(updated)).thenReturn(dto);

    AnnouncementDto result = controller.updateAnnouncement(req);

    assertSame(dto, result);
    verify(service).updateAnnouncement("New content", 3L);
  }

  // ── deleteAnnouncement ──────────────────────────────────────────────────

  @Test
  void deleteAnnouncement_delegatesToService() {
    controller.deleteAnnouncement();

    verify(service).deleteAnnouncement();
    verifyNoMoreInteractions(service, mapper);
  }
}
