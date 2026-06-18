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

package de.greluc.krt.profit.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.profit.basetool.frontend.model.form.MissionForm;
import de.greluc.krt.profit.basetool.frontend.model.form.ParticipantForm;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.profit.basetool.frontend.service.FrontendAuthHelperService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SuppressWarnings("unchecked")
class MissionPageControllerTest {

  @Test
  void createMissionForm_ShouldInitializeModelCorrectly() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    Model model = new ConcurrentModel();

    // Act
    String viewName = controller.createMissionForm(model, null);

    // Assert
    assertEquals("mission-detail", viewName);
    assertTrue(model.containsAttribute("isNew"));
    assertTrue((Boolean) model.getAttribute("isNew"));

    assertTrue(model.containsAttribute("missionForm"));
    MissionForm missionForm = (MissionForm) model.getAttribute("missionForm");
    assertNotNull(missionForm);

    assertEquals("", missionForm.name());
    assertEquals("", missionForm.description());
    assertEquals("PLANNED", missionForm.status());
    assertEquals("", missionForm.meetingTime());
    assertEquals("", missionForm.plannedStartTime());
    assertEquals("", missionForm.plannedEndTime());
  }

  @Test
  void addParticipant_ShouldCallWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));

    when(backendApiClient.post(anyString(), any(), eq(Void.class), eq(true))).thenReturn(null);

    // Act
    String view =
        controller.addParticipant(
            id,
            new ParticipantForm(null, "Guest", null, null, "Comment", null, null, null, null, null),
            mock(BindingResult.class),
            new ConcurrentModel(),
            mock(RedirectAttributes.class),
            null);

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .post(
            eq("/api/v1/missions/" + id + "/participants/add"),
            any(),
            eq(Void.class),
            eq(true)); // Should use public client for anon
  }

  @Test
  void addParticipant_AmbiguousName_ShouldExposeLocalizedToast() {
    // Backend returns 409 when the free-text participant name matches more than one
    // registered member. The frontend must expose a dedicated localized toast key so
    // users are guided to pick an entry from the autocomplete.
    UUID id = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

    when(backendApiClient.post(anyString(), any(), eq(Void.class), eq(true)))
        .thenThrow(
            new de.greluc.krt.profit.basetool.frontend.service.BackendServiceException(
                "ambiguous", null, 409));

    String view =
        controller.addParticipant(
            id,
            new ParticipantForm(
                null, "Shared Alias", null, null, "Comment", null, null, null, null, null),
            mock(BindingResult.class),
            new ConcurrentModel(),
            redirectAttributes,
            null);

    assertEquals("redirect:/missions/" + id, view);
    verify(redirectAttributes)
        .addFlashAttribute("errorToast", "error.mission.participant.ambiguous");
    verify(redirectAttributes, never())
        .addFlashAttribute("errorToast", "error.mission.participant.add");
  }

  @Test
  void addParticipant_WithUserId_Authenticated_ShouldCallWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    OidcUser user = mock(OidcUser.class);

    when(backendApiClient.post(anyString(), any(), eq(Void.class), eq(false))).thenReturn(null);

    // Act
    String view =
        controller.addParticipant(
            id,
            new ParticipantForm(userId, null, null, null, "Comment", null, null, null, null, null),
            mock(BindingResult.class),
            new ConcurrentModel(),
            mock(RedirectAttributes.class),
            user);

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .post(eq("/api/v1/missions/" + id + "/participants/add"), any(), eq(Void.class), eq(false));
  }

  @Test
  void setPartyLead_ShouldCallBackendPutAndExposeSuccessToast() {
    UUID id = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

    when(backendApiClient.put(anyString(), any(), eq(Void.class), eq(false))).thenReturn(null);

    String view = controller.setPartyLead(id, userId, "Alice", 2L, redirectAttributes);

    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .put(eq("/api/v1/missions/" + id + "/party-lead"), any(), eq(Void.class), eq(false));
    verify(redirectAttributes).addFlashAttribute("successToast", "notification.success.save");
  }

  @Test
  void setPartyLead_Conflict_ShouldExposeLocalizedToast() {
    // Backend returns 409 for an ambiguous free-text name or a stale partyLeadVersion; the
    // frontend must surface the dedicated conflict toast key rather than the generic update error.
    UUID id = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

    when(backendApiClient.put(anyString(), any(), eq(Void.class), eq(false)))
        .thenThrow(
            new de.greluc.krt.profit.basetool.frontend.service.BackendServiceException(
                "conflict", null, 409));

    String view = controller.setPartyLead(id, null, "Shared Alias", 0L, redirectAttributes);

    assertEquals("redirect:/missions/" + id, view);
    verify(redirectAttributes).addFlashAttribute("errorToast", "error.mission.party_lead.conflict");
    verify(redirectAttributes, never())
        .addFlashAttribute("errorToast", "error.mission.party_lead.update");
  }

  @Test
  void deleteParticipant_ShouldCallWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    OidcUser user = mock(OidcUser.class);

    when(backendApiClient.delete(anyString(), eq(Void.class), eq(false))).thenReturn(null);

    // Act
    String view =
        controller.deleteParticipant(id, participantId, user, mock(RedirectAttributes.class));

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .delete(
            eq("/api/v1/missions/" + id + "/participants/" + participantId),
            eq(Void.class),
            eq(false));
  }

  @Test
  void deleteParticipant_Anonymous_ShouldCallPublicWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));

    when(backendApiClient.delete(anyString(), eq(Void.class), eq(true))).thenReturn(null);

    // Act
    String view =
        controller.deleteParticipant(id, participantId, null, mock(RedirectAttributes.class));

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .delete(
            eq("/api/v1/missions/" + id + "/participants/" + participantId),
            eq(Void.class),
            eq(true));
  }

  @Test
  void updateParticipant_ShouldCallWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    OidcUser user = mock(OidcUser.class);

    when(backendApiClient.put(anyString(), any(), eq(Void.class), eq(false))).thenReturn(null);

    // Act
    String view =
        controller.updateParticipant(
            id,
            participantId,
            new ParticipantForm(
                null, null, UUID.randomUUID(), null, "New Comment", null, null, null, null, null),
            mock(BindingResult.class),
            new ConcurrentModel(),
            mock(RedirectAttributes.class),
            user);

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .put(
            eq("/api/v1/missions/" + id + "/participants/" + participantId),
            any(),
            eq(Void.class),
            eq(false));
  }

  @Test
  void updateParticipant_Anonymous_ShouldCallPublicWebClient() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));

    when(backendApiClient.put(anyString(), any(), eq(Void.class), eq(true))).thenReturn(null);

    // Act
    String view =
        controller.updateParticipant(
            id,
            participantId,
            new ParticipantForm(
                null, null, UUID.randomUUID(), null, "New Comment", null, null, null, null, null),
            mock(BindingResult.class),
            new ConcurrentModel(),
            mock(RedirectAttributes.class),
            null);

    // Assert
    assertEquals("redirect:/missions/" + id, view);
    verify(backendApiClient)
        .put(
            eq("/api/v1/missions/" + id + "/participants/" + participantId),
            any(),
            eq(Void.class),
            eq(true));
  }

  @Test
  void listMissions_ShowPastTrue_User_ShouldIncludeAllStatuses() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    Model model = new ConcurrentModel();
    OidcUser user = mock(OidcUser.class); // Mock authenticated user

    ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
    when(backendApiClient.get(
            uriCaptor.capture(), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(
            new de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse<>(
                Collections.emptyList(), 0, 0, 0, 0, Collections.emptyList()));

    // Act
    controller.listMissions(null, null, null, null, true, null, null, null, model, user);

    // Assert
    String uri = uriCaptor.getValue();
    assertTrue(uri.contains("status=COMPLETED"));
    assertTrue(uri.contains("status=CANCELLED"));
    assertTrue(uri.contains("status=PLANNED"));
    assertTrue(uri.contains("status=ACTIVE"));
    assertTrue((Boolean) model.getAttribute("showPast"));

    verify(backendApiClient).get(anyString(), any(ParameterizedTypeReference.class), eq(false));
  }

  @Test
  void listMissions_ShowPastTrue_Guest_ShouldIgnoreShowPast() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    Model model = new ConcurrentModel();
    // No user (null)

    ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
    when(backendApiClient.get(uriCaptor.capture(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(
            new de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse<>(
                Collections.emptyList(), 0, 0, 0, 0, Collections.emptyList()));

    // Act
    controller.listMissions(null, null, null, null, true, null, null, null, model, null);

    // Assert
    String uri = uriCaptor.getValue();
    // Should NOT contain past statuses despite showPast=true
    assertFalse(uri.contains("status=COMPLETED"));
    assertFalse(uri.contains("status=CANCELLED"));
    assertTrue(uri.contains("status=PLANNED"));
    assertTrue(uri.contains("status=ACTIVE"));

    // Model attribute should be false for guest
    assertFalse((Boolean) model.getAttribute("showPast"));

    verify(backendApiClient).get(anyString(), any(ParameterizedTypeReference.class), eq(true));
  }

  @Test
  void missionDetail_ShouldFetchMissionAndFilteredJobTypes() {
    // Arrange
    UUID id = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    Model model = new ConcurrentModel();

    de.greluc.krt.profit.basetool.frontend.model.dto.MissionDto mission =
        new de.greluc.krt.profit.basetool.frontend.model.dto.MissionDto(
            id,
            "Test Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            true,
            true,
            1L,
            1L,
            1L,
            1L,
            0,
            0,
            null,
            null,
            null,
            0L);

    when(backendApiClient.get(
            eq("/api/v1/missions/" + id), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    // Act
    String view = controller.missionDetail(id, model, null, null);

    // Assert
    assertEquals("mission-detail", view);
    verify(backendApiClient)
        .get(eq("/api/v1/missions/" + id), any(ParameterizedTypeReference.class), eq(true));
    verify(backendApiClient)
        .getCached(
            eq("/api/v1/job-types?archetype=MISSION&size=1000"),
            any(ParameterizedTypeReference.class),
            eq(true));
    verify(backendApiClient)
        .getCached(
            eq("/api/v1/job-types?archetype=CREW&size=1000"),
            any(ParameterizedTypeReference.class),
            eq(true));
    verify(backendApiClient)
        .getCached(
            eq("/api/v1/squadrons?size=1000"), any(ParameterizedTypeReference.class), eq(true));
  }

  @Test
  void missionDetail_Guest_ShouldNotFetchFinanceOrRefineryOrders() {
    // Arrange
    UUID id = UUID.randomUUID();
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MissionPageController controller =
        new MissionPageController(
            backendApiClient, mock(MessageSource.class), mock(FrontendAuthHelperService.class));
    Model model = new ConcurrentModel();

    de.greluc.krt.profit.basetool.frontend.model.dto.MissionDto mission =
        new de.greluc.krt.profit.basetool.frontend.model.dto.MissionDto(
            id,
            "Test Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            true,
            true,
            1L,
            1L,
            1L,
            1L,
            0,
            0,
            null,
            null,
            null,
            0L);

    when(backendApiClient.get(
            eq("/api/v1/missions/" + id), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + id + "/units?size=1000"),
            any(ParameterizedTypeReference.class),
            eq(true)))
        .thenReturn(
            new de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse<>(
                Collections.emptyList(), 0, 10, 0, 0, Collections.emptyList()));
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(Collections.emptyList());
    when(backendApiClient.getCached(
            anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
        .thenReturn(Collections.emptyList());

    // Act
    controller.missionDetail(id, model, null, null);

    // Assert
    verify(backendApiClient, never())
        .get(contains("/finance-entries"), any(ParameterizedTypeReference.class), anyBoolean());
    verify(backendApiClient, never())
        .get(contains("/refinery-orders"), any(ParameterizedTypeReference.class), anyBoolean());
    verify(backendApiClient, never())
        .get(contains("/finance-entries"), any(Class.class), anyBoolean());
  }
}
