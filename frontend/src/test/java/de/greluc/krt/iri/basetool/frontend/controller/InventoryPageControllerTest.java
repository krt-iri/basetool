package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.form.InventoryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class InventoryPageControllerTest {

  private BackendApiClient backendApiClient;
  private InventoryPageController controller;

  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    controller = new InventoryPageController(backendApiClient);
  }

  @Test
  void viewAggregatedInventory_shouldReturnIndexPage() {
    Model model = new ConcurrentModel();
    PageResponse<AggregatedInventoryDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    String view = controller.viewAggregatedInventory(null, null, model);

    assertEquals("inventory-index", view);
    assertTrue(model.containsAttribute("aggregated"));
    assertTrue(model.containsAttribute("materials"));
  }

  @Test
  void viewAggregatedInventory_shouldHandleException() {
    Model model = new ConcurrentModel();
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("Backend error"));

    String view = controller.viewAggregatedInventory(null, null, model);

    assertEquals("inventory-index", view);
    assertEquals("error.inventory.aggregate.load", model.getAttribute("error"));
  }

  @Test
  void viewMaterialInventory_shouldReturnMaterialPage() {
    Model model = new ConcurrentModel();
    UUID materialId = UUID.randomUUID();
    PageResponse<InventoryItemDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    String view = controller.viewMaterialInventory(materialId, model);

    assertEquals("inventory-material", view);
    assertTrue(model.containsAttribute("items"));
    assertEquals(materialId, model.getAttribute("selectedMaterialId"));
  }

  @Test
  void viewMyInventory_shouldReturnMyInventoryPage() {
    Model model = new ConcurrentModel();
    PageResponse<InventoryItemDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    String view = controller.viewMyInventory(null, null, null, null, false, model);

    assertEquals("inventory-my", view);
    assertTrue(model.containsAttribute("items"));
    assertTrue(model.containsAttribute("inventoryForm"));
  }

  @Test
  void viewMyInventory_shouldForwardMaterialAndMinQualityFiltersToBackend() {
    // Given
    Model model = new ConcurrentModel();
    PageResponse<InventoryItemDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
    UUID materialId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();

    // When
    String view =
        controller.viewMyInventory(
            List.of(materialId), 500, List.of(jobOrderId), null, false, model);

    // Then
    assertEquals("inventory-my", view);
    assertEquals(List.of(materialId), model.getAttribute("selectedMaterialIds"));
    assertEquals(500, model.getAttribute("selectedMinQuality"));
    assertEquals(List.of(jobOrderId), model.getAttribute("selectedJobOrderIds"));
    org.mockito.ArgumentCaptor<String> urlCaptor =
        org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(backendApiClient, org.mockito.Mockito.atLeastOnce())
        .get(urlCaptor.capture(), any(ParameterizedTypeReference.class));
    String groupedUrl =
        urlCaptor.getAllValues().stream()
            .filter(u -> u.contains("/api/v1/inventory/my-inventory/grouped"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Personal grouped endpoint was not called"));
    assertTrue(groupedUrl.contains("materialIds=" + materialId), "materialIds must be forwarded");
    assertTrue(groupedUrl.contains("minQuality=500"), "minQuality must be forwarded");
    assertTrue(
        groupedUrl.contains("jobOrderIds=" + jobOrderId),
        "jobOrderIds must be forwarded alongside new filters");
  }

  @Test
  void viewMyInventory_shouldReturnFragmentWhenRequested() {
    // Given
    Model model = new ConcurrentModel();
    PageResponse<InventoryItemDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    // When
    String view = controller.viewMyInventory(null, null, null, null, true, model);

    // Then
    assertEquals("inventory-my :: inventoryTableFragment", view);
  }

  @Test
  void viewAllInventory_shouldReturnAllInventoryPage() {
    Model model = new ConcurrentModel();
    PageResponse<InventoryItemDto> page =
        new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    String view =
        controller.viewAllInventory(List.of(UUID.randomUUID()), 100, null, null, false, model);

    assertEquals("inventory-admin", view);
    assertTrue(model.containsAttribute("items"));
  }

  @Test
  void viewInputPage_shouldReturnInputPage() {
    Model model = new ConcurrentModel();
    String view = controller.viewInputPage(null, model);
    assertEquals("inventory-input", view);
  }

  @Test
  void addInventoryItem_shouldRedirectOnSuccess() {
    InventoryForm form = new InventoryForm();
    form.setMaterialId(UUID.randomUUID());
    form.setLocationId(UUID.randomUUID());
    form.setQuality(100);
    form.setAmount(50.0);

    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(false);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    InventoryItemDto expectedDto =
        new InventoryItemDto(
            UUID.randomUUID(),
            null,
            null,
            null,
            10,
            100.0,
            false,
            null,
            null,
            null,
            null,
            null,
            1L);
    when(backendApiClient.post(anyString(), any(), eq(InventoryItemDto.class)))
        .thenReturn(expectedDto);

    String view =
        controller.addInventoryItem(form, bindingResult, new ConcurrentModel(), redirectAttributes);

    assertEquals("redirect:/inventory", view);
    assertTrue(redirectAttributes.getFlashAttributes().containsKey("successToast"));
  }

  @Test
  void addInventoryItem_shouldHandleValidationError() {
    InventoryForm form = new InventoryForm();
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(true);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    String view =
        controller.addInventoryItem(form, bindingResult, new ConcurrentModel(), redirectAttributes);

    // After the render-instead-redirect refactor a validation error renders the
    // input view inline rather than redirecting; BindingResult stays request-scoped
    // (see RedisSessionConfig — no more self-referencing flash attribute).
    assertEquals("inventory-input", view);
  }

  @Test
  void addInventoryItem_shouldHandleBackendException() {
    InventoryForm form = new InventoryForm();
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(false);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    when(backendApiClient.post(anyString(), any(), eq(InventoryItemDto.class)))
        .thenThrow(new RuntimeException("Error"));

    String view =
        controller.addInventoryItem(form, bindingResult, new ConcurrentModel(), redirectAttributes);

    assertEquals("redirect:/inventory/input", view);
    assertTrue(redirectAttributes.getFlashAttributes().containsKey("errorToast"));
  }

  @Test
  void bookOutInventoryItem_shouldRedirectOnSuccess() {
    UUID id = UUID.randomUUID();
    de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form =
        new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
    form.setAmount(10.0);
    form.setVersion(1L);
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(false);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    when(backendApiClient.post(anyString(), any(), eq(Void.class))).thenReturn(null);

    String view =
        controller.bookOutInventoryItem(
            id, form, bindingResult, new ConcurrentModel(), redirectAttributes, "/inventory/all");

    assertEquals("redirect:/inventory/all", view);
    assertTrue(redirectAttributes.getFlashAttributes().containsKey("successToast"));
  }

  @Test
  void bookOutInventoryItem_shouldHandleBackendException() {
    UUID id = UUID.randomUUID();
    de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form =
        new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(false);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    when(backendApiClient.post(anyString(), any(), eq(Void.class)))
        .thenThrow(new RuntimeException("Update error"));

    String view =
        controller.bookOutInventoryItem(
            id, form, bindingResult, new ConcurrentModel(), redirectAttributes, "/inventory/all");

    assertEquals("redirect:/inventory/all", view);
    assertTrue(redirectAttributes.getFlashAttributes().containsKey("errorToast"));
  }

  @Test
  void bookOutInventoryItem_shouldPreserveFiltersFromRefererOnSuccess() {
    UUID id = UUID.randomUUID();
    de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form =
        new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
    form.setAmount(5.0);
    form.setVersion(1L);
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(false);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    when(backendApiClient.post(anyString(), any(), eq(Void.class))).thenReturn(null);

    String referer =
        "https://example.org/inventory/my?materialIds=11111111-1111-1111-1111-111111111111&minQuality=50&jobOrderIds=22222222-2222-2222-2222-222222222222&fragment=true";
    String view =
        controller.bookOutInventoryItem(
            id, form, bindingResult, new ConcurrentModel(), redirectAttributes, referer);

    assertEquals(
        "redirect:/inventory/my?materialIds=11111111-1111-1111-1111-111111111111&minQuality=50&jobOrderIds=22222222-2222-2222-2222-222222222222",
        view);
    assertTrue(redirectAttributes.getFlashAttributes().containsKey("successToast"));
  }

  @Test
  void bookOutInventoryItem_shouldPreserveFiltersForAdminView() {
    UUID id = UUID.randomUUID();
    de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form =
        new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
    form.setAmount(5.0);
    form.setVersion(1L);
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.hasErrors()).thenReturn(true);
    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    String referer =
        "https://example.org/inventory/all?missionIds=33333333-3333-3333-3333-333333333333&page=2";
    ConcurrentModel renderModel = new ConcurrentModel();
    String view =
        controller.bookOutInventoryItem(
            id, form, bindingResult, renderModel, redirectAttributes, referer);

    // After the render-instead-redirect refactor a validation error during book-out
    // re-renders the originating listing (admin variant here) inline; the URL filters
    // are not re-applied, the errorToast lives on the request-scoped Model instead.
    assertEquals("inventory-admin", view);
    assertEquals("error.validation.failed", renderModel.getAttribute("errorToast"));
    assertEquals(id, renderModel.getAttribute("showBookOutModal"));
  }

  @Test
  void buildInventoryRedirectFromReferer_shouldHandleNullAndEmptyReferer() {
    assertEquals(
        "/inventory/my",
        de.greluc.krt.iri.basetool.frontend.controller.InventoryPageController
            .buildInventoryRedirectFromReferer("/inventory/my", null));
    assertEquals(
        "/inventory/my",
        de.greluc.krt.iri.basetool.frontend.controller.InventoryPageController
            .buildInventoryRedirectFromReferer("/inventory/my", ""));
    assertEquals(
        "/inventory/my",
        de.greluc.krt.iri.basetool.frontend.controller.InventoryPageController
            .buildInventoryRedirectFromReferer(
                "/inventory/my", "https://example.org/inventory/my"));
    assertEquals(
        "/inventory/all",
        de.greluc.krt.iri.basetool.frontend.controller.InventoryPageController
            .buildInventoryRedirectFromReferer(
                "/inventory/all", "https://example.org/inventory/all?fragment=true"));
  }

  @Test
  void updateAssociations_shouldReturnOkOnSuccess() {
    UUID id = UUID.randomUUID();
    InventoryItemUpdateDto dto =
        new InventoryItemUpdateDto(
            UUID.randomUUID(), UUID.randomUUID(), 100, 10.0, false, null, null, 1L);

    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class))).thenReturn(null);

    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateAssociations(id, dto);

    assertEquals(200, response.getStatusCode().value());
    verify(backendApiClient)
        .put(eq("/api/v1/inventory/" + id), eq(dto), eq(InventoryItemDto.class));
  }

  @Test
  void updateAssociations_shouldReturnStatusFromWebClientResponseException() {
    UUID id = UUID.randomUUID();
    InventoryItemUpdateDto dto =
        new InventoryItemUpdateDto(
            UUID.randomUUID(), UUID.randomUUID(), 100, 10.0, false, null, null, 1L);

    org.springframework.web.reactive.function.client.WebClientResponseException exception =
        org.springframework.web.reactive.function.client.WebClientResponseException.create(
            409, "Conflict", org.springframework.http.HttpHeaders.EMPTY, null, null);
    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class))).thenThrow(exception);

    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateAssociations(id, dto);

    assertEquals(409, response.getStatusCode().value());
  }

  @Test
  void updateAssociations_shouldReturn500OnGenericException() {
    UUID id = UUID.randomUUID();
    InventoryItemUpdateDto dto =
        new InventoryItemUpdateDto(
            UUID.randomUUID(), UUID.randomUUID(), 100, 10.0, false, null, null, 1L);

    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class)))
        .thenThrow(new RuntimeException("Generic error"));

    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateAssociations(id, dto);

    assertEquals(500, response.getStatusCode().value());
  }

  @Test
  void updateInventoryItemNote_shouldReturnOkWithUpdatedDtoOnSuccess() {
    // Given
    UUID id = UUID.randomUUID();
    InventoryItemNoteUpdateRequest request = new InventoryItemNoteUpdateRequest("hello", 1L);
    InventoryItemDto updated =
        new InventoryItemDto(
            id, null, null, null, 100, 10.0, false, null, null, null, null, "hello", 2L);
    when(backendApiClient.put(
            eq("/api/v1/inventory/" + id + "/note"), eq(request), eq(InventoryItemDto.class)))
        .thenReturn(updated);

    // When
    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateInventoryItemNote(id, request);

    // Then
    assertEquals(200, response.getStatusCode().value());
    assertSame(updated, response.getBody());
  }

  @Test
  void updateInventoryItemNote_shouldPropagate409FromBackendServiceException() {
    // Given: backend returned 409 CONFLICT (wrapped in BackendServiceException by BackendApiClient)
    UUID id = UUID.randomUUID();
    InventoryItemNoteUpdateRequest request = new InventoryItemNoteUpdateRequest("hello", 1L);
    de.greluc.krt.iri.basetool.frontend.service.BackendServiceException ex =
        new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
            "Backend service returned error: 409 CONFLICT", null, 409);
    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class))).thenThrow(ex);

    // When
    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateInventoryItemNote(id, request);

    // Then: must be 409, NOT 500, so the JS modal can react (toast + reload) instead of
    // treating the response as a generic server error.
    assertEquals(409, response.getStatusCode().value());
  }

  @Test
  void updateInventoryItemNote_shouldPropagate403FromBackendServiceException() {
    UUID id = UUID.randomUUID();
    InventoryItemNoteUpdateRequest request = new InventoryItemNoteUpdateRequest("hello", 1L);
    de.greluc.krt.iri.basetool.frontend.service.BackendServiceException ex =
        new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException(
            "Backend service returned error: 403 FORBIDDEN", null, 403);
    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class))).thenThrow(ex);

    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateInventoryItemNote(id, request);

    assertEquals(403, response.getStatusCode().value());
  }

  @Test
  void updateInventoryItemNote_shouldReturn500OnGenericException() {
    UUID id = UUID.randomUUID();
    InventoryItemNoteUpdateRequest request = new InventoryItemNoteUpdateRequest("hello", 1L);
    when(backendApiClient.put(anyString(), any(), eq(InventoryItemDto.class)))
        .thenThrow(new RuntimeException("boom"));

    org.springframework.http.ResponseEntity<InventoryItemDto> response =
        controller.updateInventoryItemNote(id, request);

    assertEquals(500, response.getStatusCode().value());
  }
}
