package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@SuppressWarnings("unchecked")
class MissionPageControllerMvcTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void createMission_WithEmptyDescription_ShouldSucceed() throws Exception {
    // Prepare Mocks
    when(backendApiClient.post(any(String.class), any(), Mockito.eq(Void.class))).thenReturn(null);

    // Perform Request
    mockMvc
        .perform(
            post("/missions")
                .param("name", "Test Mission")
                .param("description", "") // Empty description triggers StringTrimmerEditor -> null
                .param("status", "PLANNED")
                .param("plannedStartTime", "2026-02-10T10:00")
                .param("plannedEndTime", "2026-02-10T12:00")
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(redirectedUrl("/missions"));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void missionDetail_ShouldRenderWithoutErrors() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    Map<String, Object> shipTypeData = new java.util.HashMap<>();
    shipTypeData.put("id", UUID.randomUUID().toString());
    shipTypeData.put("name", "Fighter");

    Map<String, Object> unitData = new java.util.HashMap<>();
    unitData.put("id", unitId.toString());
    unitData.put("name", "Alpha Unit");
    unitData.put("highValueUnit", false);
    unitData.put("shipType", shipTypeData);
    unitData.put("ship", null);
    unitData.put("frequency", 123.45);
    unitData.put("crew", new java.util.ArrayList<>());

    Map<String, Object> userData = new java.util.HashMap<>();
    userData.put("username", "TestUser");
    userData.put("displayName", "Test User");

    Map<String, Object> locationData = new java.util.HashMap<>();
    locationData.put("name", "Test Location");

    Map<String, Object> orderData = new java.util.HashMap<>();
    orderData.put("id", UUID.randomUUID().toString());
    orderData.put("startedAt", "2026-03-29T18:00:00Z");
    orderData.put("status", "OPEN");
    orderData.put("location", locationData);
    orderData.put("owner", userData);
    orderData.put("durationMinutes", 60);
    orderData.put("goods", new java.util.ArrayList<>());

    Map<String, Object> itemData = new java.util.HashMap<>();
    itemData.put("id", UUID.randomUUID().toString());
    itemData.put("user", userData);
    itemData.put("location", locationData);

    Map<String, Object> materialData = new java.util.HashMap<>();
    materialData.put("name", "Quantanium");
    itemData.put("material", materialData);

    itemData.put("amount", 100);
    itemData.put("quality", 50);
    itemData.put("jobOrderDisplayId", 123);

    UUID managerId = UUID.randomUUID();
    de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto manager =
        new de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto(
            managerId, "manager", null, "Test Manager", 0);

    MissionDto mission =
        new MissionDto(
            missionId,
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
            java.util.Set.of(manager),
            true,
            true,
            1L,
            0,
            0);

    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    // This will fail with TemplateProcessingException if the th:onclick syntax is invalid
    mockMvc
        .perform(get("/missions/" + missionId))
        .andExpect(status().isOk())
        .andExpect(view().name("mission-detail"))
        // Responsive panel grid (no horizontal scroll)
        .andExpect(content().string(containsString("mission-columns-container")))
        // Accessible collapse buttons with aria-expanded and data-panel-key
        .andExpect(content().string(containsString("data-panel-key=\"details\"")))
        .andExpect(content().string(containsString("data-panel-key=\"participants\"")))
        .andExpect(content().string(containsString("data-panel-key=\"units\"")))
        .andExpect(content().string(containsString("data-panel-key=\"finance\"")))
        .andExpect(content().string(containsString("data-panel-toggle=\"col-details\"")))
        .andExpect(content().string(containsString("aria-expanded=\"true\"")))
        .andExpect(content().string(containsString("aria-controls=\"col-details-content\"")))
        // Legacy horizontal-scroll markers must be gone
        .andExpect(content().string(not(containsString("vertical-title"))))
        // Content-aware per-panel min-widths so wide tables/action
        // buttons never overflow their panel; also drives the
        // 1-column-fallback when two panels don't fit side by side.
        .andExpect(content().string(containsString("--panel-min-width")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "[data-panel-key=\"participants\"] { --panel-min-width: 1100px;")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "[data-panel-key=\"units\"]        { --panel-min-width: 1100px;")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "[data-panel-key=\"payout\"]       { --panel-min-width: 820px;")))
        // Ultra-wide breakpoint explicitly caps column count so wide
        // panels like "participants" never get squeezed on 4K/UHD.
        .andExpect(content().string(containsString("@media (min-width: 2400px)")))
        .andExpect(content().string(containsString("@media (min-width: 3600px)")))
        // Regression guard: .table-responsive must not have a default
        // overflow-x: auto anymore (that caused intra-panel horizontal
        // scrolling instead of letting the panel grow wide enough).
        .andExpect(
            content()
                .string(
                    not(
                        containsString(
                            ".mission-column .col-content .table-responsive {\n"
                                + "            width: 100%;\n"
                                + "            /* Intra-panel horizontal scroll is a last-resort"
                                + " fallback"))));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void missionDetail_AsAuthenticated_ShouldShowParticipationColumn() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            participantId,
            null,
            "P1",
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
            "Auth Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            java.util.Set.of(participant),
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
            1,
            1);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/missions/" + missionId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Teilnahme (%)")));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void missionDetail_ShouldRenderEditButtonWithCheckInTimes() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();

    java.time.Instant checkIn = java.time.Instant.parse("2026-02-10T09:30:00Z");
    java.time.Instant checkOut = java.time.Instant.parse("2026-02-10T11:45:00Z");

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            participantId,
            null,
            "P1",
            null,
            null,
            null,
            null,
            checkIn,
            checkOut,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
            "Repro Mission",
            null,
            null,
            "RUNNING",
            null,
            null,
            java.time.Instant.parse("2026-02-10T09:00:00Z"),
            null,
            null,
            false,
            java.util.Set.of(participant),
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
            1,
            1);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/missions/" + missionId))
        .andExpect(status().isOk())
        // Raw UTC timestamps must be present on the edit button
        .andExpect(content().string(containsString("data-start-time=\"2026-02-10T09:30:00Z\"")))
        .andExpect(content().string(containsString("data-end-time=\"2026-02-10T11:45:00Z\"")))
        // Formatted (local-zone) timestamps must also be present and non-empty so the
        // participant edit modal can pre-populate datetime-local inputs.
        .andExpect(content().string(not(containsString("data-start-time-formatted=\"\""))))
        .andExpect(content().string(not(containsString("data-end-time-formatted=\"\""))))
        // Regression guard: the edit modal must re-sync the datetime-split widget
        // after programmatically setting the hidden value (otherwise the visible
        // date/time inputs stay empty even though startTime/endTime are present).
        .andExpect(content().string(containsString("krtSyncDatetimeSplitGroup")));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void missionDetail_ShouldRenderParticipantsCounter_AsXSlashY() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID p1Id = UUID.randomUUID();
    UUID p2Id = UUID.randomUUID();
    UUID p3Id = UUID.randomUUID();

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p1 =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            p1Id,
            null,
            "P1",
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);
    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p2 =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            p2Id,
            null,
            "P2",
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);
    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p3 =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            p3Id,
            null,
            "P3",
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    // 2 checked-in out of 3 registered
    MissionDto mission =
        new MissionDto(
            missionId,
            "Counter Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            java.util.Set.of(p1, p2, p3),
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
            2,
            3);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/missions/" + missionId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2/3")));
  }

  @Test
  void missionDetail_AsGuest_ShouldHideParticipationColumn() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            participantId,
            null,
            "P1",
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
            "Guest Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            java.util.Set.of(participant),
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
            0,
            1);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(mission);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/missions/" + missionId))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Teilnahme (%)"))));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void setMissionOwner_WithValidIds_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId + "/owner/" + userId),
            eq(null),
            eq(Void.class),
            eq(false)))
        .thenReturn(null);

    mockMvc
        .perform(put("/missions/" + missionId + "/owner/" + userId).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void setMissionOwner_WithInvalidMissionId_ShouldReturn400() throws Exception {
    mockMvc
        .perform(put("/missions/not-a-uuid/owner/" + UUID.randomUUID()).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void addManager_WithInvalidIds_ShouldReturn400() throws Exception {
    mockMvc
        .perform(post("/missions/not-a-uuid/managers/not-a-uuid").with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateActualTime_Success_ShouldReturn200WithRefreshedMission() throws Exception {
    UUID missionId = UUID.randomUUID();
    MissionDto current =
        new MissionDto(
            missionId,
            "M",
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
            0,
            0);
    MissionDto refreshed =
        new MissionDto(
            missionId,
            "M",
            null,
            null,
            "PLANNED",
            null,
            null,
            java.time.Instant.parse("2026-04-20T12:00:00Z"),
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
            2L,
            0,
            0);
    when(backendApiClient.get(eq("/api/v1/missions/" + missionId), eq(MissionDto.class)))
        .thenReturn(current)
        .thenReturn(refreshed);
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId), any(MissionDto.class), eq(Void.class)))
        .thenReturn(null);

    String body =
        "{\"field\":\"actualStartTime\",\"value\":\"2026-04-20T12:00:00Z\",\"version\":1}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/actual-time")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateActualTime_OptimisticLockConflict_ShouldReturn409() throws Exception {
    UUID missionId = UUID.randomUUID();
    MissionDto current =
        new MissionDto(
            missionId,
            "M",
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
            5L,
            0,
            0);
    when(backendApiClient.get(eq("/api/v1/missions/" + missionId), eq(MissionDto.class)))
        .thenReturn(current);
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId), any(MissionDto.class), eq(Void.class)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    String body = "{\"field\":\"actualEndTime\",\"value\":\"2026-04-20T13:00:00Z\",\"version\":1}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/actual-time")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateActualTime_InvalidField_ShouldReturn400() throws Exception {
    UUID missionId = UUID.randomUUID();
    String body = "{\"field\":\"unknownField\",\"value\":\"2026-04-20T13:00:00Z\",\"version\":1}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/actual-time")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void addManager_WithBackendError_ShouldPropagateStatus() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/managers/" + userId + "/slim"),
            eq(null),
            eq(String.class),
            eq(false)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Error", null, 400));

    mockMvc
        .perform(post("/missions/" + missionId + "/managers/" + userId).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  // --- Paket 3B: Frequencies AJAX endpoints -----------------------------

  @Test
  @WithMockUser(roles = "OFFICER")
  void addOrUpdateFrequencyAjax_WithValidBody_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID freqTypeId = UUID.randomUUID();
    java.util.List<Map<String, Object>> slimResponse = new java.util.ArrayList<>();
    Map<String, Object> freq = new java.util.HashMap<>();
    freq.put("id", UUID.randomUUID().toString());
    Map<String, Object> ft = new java.util.HashMap<>();
    ft.put("id", freqTypeId.toString());
    ft.put("name", "Tac");
    freq.put("frequencyType", ft);
    freq.put("value", 123.45);
    freq.put("version", 1);
    slimResponse.add(freq);

    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/frequencies/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenReturn(slimResponse);

    String body = "{\"frequencyTypeId\":\"" + freqTypeId + "\",\"value\":123.45}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/frequencies/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(freqTypeId.toString())));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void addOrUpdateFrequencyAjax_WithBackendError_ShouldPropagateStatus() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID freqTypeId = UUID.randomUUID();
    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/frequencies/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    String body = "{\"frequencyTypeId\":\"" + freqTypeId + "\",\"value\":123.45}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/frequencies/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void deleteFrequencyAjax_Success_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID freqId = UUID.randomUUID();
    when(backendApiClient.delete(
            eq("/api/v1/missions/" + missionId + "/frequencies/" + freqId + "/slim"),
            eq(Object.class),
            eq(false)))
        .thenReturn(java.util.Collections.emptyList());

    mockMvc
        .perform(delete("/missions/" + missionId + "/frequencies/" + freqId + "/ajax").with(csrf()))
        .andExpect(status().isOk());
  }

  // --- Paket 3C: Units AJAX endpoints -----------------------------------

  @Test
  @WithMockUser(roles = "OFFICER")
  void addUnitAjax_WithValidBody_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    java.util.List<Map<String, Object>> slimResponse = new java.util.ArrayList<>();
    Map<String, Object> unit = new java.util.HashMap<>();
    unit.put("id", UUID.randomUUID().toString());
    unit.put("name", "Alpha");
    unit.put("highValueUnit", false);
    unit.put("version", 0);
    slimResponse.add(unit);

    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/units/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenReturn(slimResponse);

    String body = "{\"name\":\"Alpha\",\"highValueUnit\":false}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/units/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Alpha")));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateUnitAjax_WithBackendConflict_ShouldReturn409() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId + "/units/" + unitId + "/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    String body = "{\"name\":\"Alpha\",\"highValueUnit\":false}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/units/" + unitId + "/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void deleteUnitAjax_Success_ShouldReturn204() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(backendApiClient.delete(
            eq("/api/v1/missions/" + missionId + "/units/" + unitId + "/slim"),
            eq(Void.class),
            eq(false)))
        .thenReturn(null);

    mockMvc
        .perform(delete("/missions/" + missionId + "/units/" + unitId + "/ajax").with(csrf()))
        .andExpect(status().isNoContent());
  }

  // --- Paket 3C (Option b): Participants AJAX endpoints ------------------

  @Test
  @WithMockUser(roles = "OFFICER")
  void addParticipantAjax_WithValidBody_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    java.util.List<Map<String, Object>> slimResponse = new java.util.ArrayList<>();
    Map<String, Object> p = new java.util.HashMap<>();
    p.put("id", UUID.randomUUID().toString());
    p.put("guestName", "Guest-X");
    p.put("version", 0);
    slimResponse.add(p);

    // Note: since the bugfix for anonymous mission signups, this controller method
    // routes via `isPublic = (principal == null)` — `@WithMockUser` does not produce
    // an `OidcUser` principal, so `isPublic` evaluates to `true` here. The dedicated
    // happy-path test for an authenticated OIDC user lives in
    // `addParticipantAjax_AsAnonymousGuest_ShouldRouteThroughPublicWebClientAndReturn200`
    // (anonymous) and is implicitly covered by the production wiring; for this MVC
    // smoke-test we therefore accept any boolean for the isPublic flag and only
    // assert that the controller forwards to the correct backend slim endpoint.
    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/participants/slim"),
            any(),
            eq(Object.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(slimResponse);

    String body = "{\"guestName\":\"Guest-X\"}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/participants/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Guest-X")));
  }

  /**
   * Reproducer for the "anonymous guest cannot sign up to a mission" bug (see live-log/log.txt:
   * repeated `AccessDeniedException` on POST /missions/{id}/participants/ajax for `[anonymous]`).
   *
   * <p>Given an anonymous caller (no `@WithMockUser`), when the AJAX add-participant endpoint is
   * invoked with a guestName payload, then the request must be routed through the public WebClient
   * (`isPublic=true`) and return HTTP 200 with the slim response - it must NOT be blocked by Spring
   * Security with 403.
   */
  @Test
  void addParticipantAjax_AsAnonymousGuest_ShouldRouteThroughPublicWebClientAndReturn200()
      throws Exception {
    UUID missionId = UUID.randomUUID();
    java.util.List<Map<String, Object>> slimResponse = new java.util.ArrayList<>();
    Map<String, Object> p = new java.util.HashMap<>();
    p.put("id", UUID.randomUUID().toString());
    p.put("guestName", "Anon-Guest");
    p.put("version", 0);
    slimResponse.add(p);

    // The fix flips isPublic to true when no OidcUser principal is present, so
    // the stub MUST match isPublic=true for an anonymous caller.
    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/participants/slim"),
            any(),
            eq(Object.class),
            eq(true)))
        .thenReturn(slimResponse);

    String body = "{\"guestName\":\"Anon-Guest\"}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/participants/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Anon-Guest")));
  }

  /**
   * Reproducer for the "anonymous guest cannot edit/delete their own participant entry" bug (see
   * live-log/log.txt: repeated 403 on PUT and DELETE /missions/{id}/participants/{pid}/ajax for
   * `[anonymous]`).
   *
   * <p>Given an anonymous caller, the AJAX update/delete endpoints must route through the public
   * WebClient (`isPublic=true`) and not be blocked by Spring Security.
   */
  @Test
  void updateParticipantAjax_AsAnonymousGuest_ShouldRouteThroughPublicWebClientAndReturn200()
      throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    Map<String, Object> slimResponse = new java.util.HashMap<>();
    slimResponse.put("id", participantId.toString());
    slimResponse.put("guestName", "Anon-Guest");
    slimResponse.put("version", 1);
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId + "/participants/" + participantId + "/slim"),
            any(),
            eq(Object.class),
            eq(true)))
        .thenReturn(slimResponse);

    String body = "{\"version\":0,\"guestName\":\"Anon-Guest\",\"comment\":\"edited\"}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/participants/" + participantId + "/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Anon-Guest")));
  }

  @Test
  void deleteParticipantAjax_AsAnonymousGuest_ShouldRouteThroughPublicWebClientAndReturn204()
      throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    when(backendApiClient.delete(
            eq("/api/v1/missions/" + missionId + "/participants/" + participantId + "/slim"),
            eq(Void.class),
            eq(true)))
        .thenReturn(null);

    mockMvc
        .perform(
            delete("/missions/" + missionId + "/participants/" + participantId + "/ajax")
                .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateParticipantAjax_WithBackendConflict_ShouldReturn409() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    // Note: since the bugfix for anonymous mission participant edits, this controller
    // method routes via `isPublic = (principal == null)`. `@WithMockUser` does not
    // produce an `OidcUser` principal, so isPublic evaluates to true here. Accept any
    // boolean for the isPublic flag in this MVC smoke-test.
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId + "/participants/" + participantId + "/slim"),
            any(),
            eq(Object.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    String body = "{\"version\":1,\"comment\":\"test\"}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/participants/" + participantId + "/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void deleteParticipantAjax_Success_ShouldReturn204() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    when(backendApiClient.delete(
            eq("/api/v1/missions/" + missionId + "/participants/" + participantId + "/slim"),
            eq(Void.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(null);

    mockMvc
        .perform(
            delete("/missions/" + missionId + "/participants/" + participantId + "/ajax")
                .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void checkInParticipantAjax_Success_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    Map<String, Object> slimResponse = new java.util.HashMap<>();
    slimResponse.put("id", participantId.toString());
    slimResponse.put("version", 2);
    when(backendApiClient.post(
            eq(
                "/api/v1/missions/"
                    + missionId
                    + "/participants/"
                    + participantId
                    + "/check-in/slim"),
            any(),
            eq(Object.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(slimResponse);

    mockMvc
        .perform(
            post("/missions/" + missionId + "/participants/" + participantId + "/check-in/ajax")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(participantId.toString())));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void checkOutParticipantAjax_WithBackendError_ShouldPropagateStatus() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    when(backendApiClient.post(
            eq(
                "/api/v1/missions/"
                    + missionId
                    + "/participants/"
                    + participantId
                    + "/check-out/slim"),
            any(),
            eq(Object.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    mockMvc
        .perform(
            post("/missions/" + missionId + "/participants/" + participantId + "/check-out/ajax")
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  // --- Paket 3C (Option c): Crew AJAX endpoints --------------------------

  @Test
  @WithMockUser(roles = "OFFICER")
  void addCrewAjax_WithValidBody_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    java.util.List<Map<String, Object>> slimResponse = new java.util.ArrayList<>();
    Map<String, Object> crew = new java.util.HashMap<>();
    crew.put("id", UUID.randomUUID().toString());
    crew.put("participantName", "Alice");
    crew.put("version", 0);
    slimResponse.add(crew);

    when(backendApiClient.post(
            eq("/api/v1/missions/" + missionId + "/units/" + unitId + "/crew/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenReturn(slimResponse);

    String body = "{\"participantId\":\"" + UUID.randomUUID() + "\",\"jobTypeIds\":[]}";
    mockMvc
        .perform(
            post("/missions/" + missionId + "/units/" + unitId + "/crew/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Alice")));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void updateCrewAjax_WithBackendConflict_ShouldReturn409() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();
    when(backendApiClient.put(
            eq("/api/v1/missions/" + missionId + "/units/" + unitId + "/crew/" + crewId + "/slim"),
            any(),
            eq(Object.class),
            eq(false)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Conflict", null, 409));

    String body = "{\"jobTypeIds\":[]}";
    mockMvc
        .perform(
            put("/missions/" + missionId + "/units/" + unitId + "/crew/" + crewId + "/ajax")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void deleteCrewAjax_Success_ShouldReturn204() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();
    when(backendApiClient.delete(
            eq("/api/v1/missions/" + missionId + "/units/" + unitId + "/crew/" + crewId + "/slim"),
            eq(Void.class),
            eq(false)))
        .thenReturn(null);

    mockMvc
        .perform(
            delete("/missions/" + missionId + "/units/" + unitId + "/crew/" + crewId + "/ajax")
                .with(csrf()))
        .andExpect(status().isNoContent());
  }

  // --- Bugfix: MEMBER sieht Bearbeiten-Button für eigenen Teilnehmereintrag ---

  @Test
  void missionDetail_AsMember_ShouldShowEditButtonForOwnParticipantEntry() throws Exception {
    // Given
    UUID missionId = UUID.randomUUID();
    UUID memberUserId = UUID.randomUUID(); // Keycloak sub der eingeloggten Person
    UUID participantId = UUID.randomUUID();

    de.greluc.krt.iri.basetool.frontend.model.dto.UserDto memberUser =
        new de.greluc.krt.iri.basetool.frontend.model.dto.UserDto(
            memberUserId,
            "member1",
            "Member One",
            "Member One",
            "Member",
            "One",
            "member@example.com",
            1,
            null,
            java.util.Set.of("MEMBER"),
            java.util.Set.of(),
            null,
            false,
            false,
            true,
            1L,
            null);

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            participantId,
            memberUser,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
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
            java.util.Set.of(participant),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            false, // canEdit = false (kein Manager/Admin)
            false,
            1L,
            1,
            1);

    de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse<Object> emptyPage =
        new de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse<>(
            Collections.emptyList(), 0, 0, 0, 0, Collections.emptyList());
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(mission);
    when(backendApiClient.getCached(
            anyString(),
            any(ParameterizedTypeReference.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(emptyPage);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(emptyPage);
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(emptyPage);
    when(backendApiClient.get(anyString(), any(Class.class), eq(false))).thenReturn(null);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(mission);

    // When / Then: MEMBER sieht den Bearbeiten-Button für seinen eigenen Eintrag
    mockMvc
        .perform(
            get("/missions/" + missionId)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.oidcLogin()
                        .idToken(
                            token ->
                                token
                                    .subject(memberUserId.toString())
                                    .claim("preferred_username", "member1"))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("class=\"btn edit-participant-btn\"")));
  }

  @Test
  void missionDetail_AsMember_ShouldNotShowEditButtonForForeignParticipantEntry() throws Exception {
    // Given
    UUID missionId = UUID.randomUUID();
    UUID loggedInUserId = UUID.randomUUID(); // eingeloggter MEMBER
    UUID otherUserId = UUID.randomUUID(); // anderer Nutzer
    UUID participantId = UUID.randomUUID();

    de.greluc.krt.iri.basetool.frontend.model.dto.UserDto otherUser =
        new de.greluc.krt.iri.basetool.frontend.model.dto.UserDto(
            otherUserId,
            "other1",
            "Other One",
            "Other One",
            "Other",
            "One",
            "other@example.com",
            1,
            null,
            java.util.Set.of("MEMBER"),
            java.util.Set.of(),
            null,
            false,
            false,
            true,
            1L,
            null);

    de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant =
        new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
            participantId,
            otherUser,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
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
            java.util.Set.of(participant),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            false, // canEdit = false (kein Manager/Admin)
            false,
            1L,
            1,
            1);

    de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse<Object> emptyPage2 =
        new de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse<>(
            Collections.emptyList(), 0, 0, 0, 0, Collections.emptyList());
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(mission);
    when(backendApiClient.getCached(
            anyString(),
            any(ParameterizedTypeReference.class),
            org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(emptyPage2);
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(emptyPage2);
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(emptyPage2);
    when(backendApiClient.get(anyString(), any(Class.class), eq(false))).thenReturn(null);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(false)))
        .thenReturn(mission);

    // When / Then: MEMBER sieht den Bearbeiten-Button NICHT für fremde Einträge
    mockMvc
        .perform(
            get("/missions/" + missionId)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.oidcLogin()
                        .idToken(
                            token ->
                                token
                                    .subject(loggedInUserId.toString())
                                    .claim("preferred_username", "member2"))))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("class=\"btn edit-participant-btn\""))));
  }

  // --- Unassigned participants AJAX endpoint ------------------------------

  @Test
  @WithMockUser(roles = "OFFICER")
  void getUnassignedParticipantsAjax_ShouldReturn200WithList() throws Exception {
    // Given
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();
    Map<String, Object> participant = new java.util.HashMap<>();
    participant.put("id", participantId.toString());
    participant.put("guestName", "Alice");
    List<Map<String, Object>> response = List.of(participant);

    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId + "/participants/unassigned"),
            any(ParameterizedTypeReference.class),
            eq(false)))
        .thenReturn(response);

    // When / Then
    mockMvc
        .perform(get("/missions/" + missionId + "/participants/unassigned/ajax"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(participantId.toString())));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void getUnassignedParticipantsAjax_WithBackendError_ShouldPropagateStatus() throws Exception {
    // Given
    UUID missionId = UUID.randomUUID();
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId + "/participants/unassigned"),
            any(ParameterizedTypeReference.class),
            eq(false)))
        .thenThrow(
            new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
                "Not Found", null, 404));

    // When / Then
    mockMvc
        .perform(get("/missions/" + missionId + "/participants/unassigned/ajax"))
        .andExpect(status().isNotFound());
  }
}
