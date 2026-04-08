package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.form.InventoryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        PageResponse<AggregatedInventoryDto> page = new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
        
        String view = controller.viewAggregatedInventory(null, null, model);
        
        assertEquals("inventory-index", view);
        assertTrue(model.containsAttribute("aggregated"));
        assertTrue(model.containsAttribute("materials"));
    }

    @Test
    void viewAggregatedInventory_shouldHandleException() {
        Model model = new ConcurrentModel();
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("Backend error"));
        
        String view = controller.viewAggregatedInventory(null, null, model);
        
        assertEquals("inventory-index", view);
        assertEquals("error.inventory.aggregate.load", model.getAttribute("error"));
    }

    @Test
    void viewMaterialInventory_shouldReturnMaterialPage() {
        Model model = new ConcurrentModel();
        UUID materialId = UUID.randomUUID();
        PageResponse<InventoryItemDto> page = new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
        
        String view = controller.viewMaterialInventory(materialId, model);
        
        assertEquals("inventory-material", view);
        assertTrue(model.containsAttribute("items"));
        assertEquals(materialId, model.getAttribute("selectedMaterialId"));
    }

    @Test
    void viewMyInventory_shouldReturnMyInventoryPage() {
        Model model = new ConcurrentModel();
        PageResponse<InventoryItemDto> page = new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
        
        String view = controller.viewMyInventory(model);
        
        assertEquals("inventory-my", view);
        assertTrue(model.containsAttribute("items"));
        assertTrue(model.containsAttribute("inventoryForm"));
    }

    @Test
    void viewAllInventory_shouldReturnAllInventoryPage() {
        Model model = new ConcurrentModel();
        PageResponse<InventoryItemDto> page = new PageResponse<>(List.of(), 0, 1, 0, 1, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
        
        String view = controller.viewAllInventory(List.of(UUID.randomUUID()), 100, false, model);
        
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

        InventoryItemDto expectedDto = new InventoryItemDto(UUID.randomUUID(), null, null, null, 10, 100.0, false, null, null, null, null, 1L);
        when(backendApiClient.post(anyString(), any(), eq(InventoryItemDto.class))).thenReturn(expectedDto);
        
        String view = controller.addInventoryItem(form, bindingResult, redirectAttributes);
        
        assertEquals("redirect:/inventory", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("successToast"));
    }

    @Test
    void addInventoryItem_shouldHandleValidationError() {
        InventoryForm form = new InventoryForm();
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(true);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        
        String view = controller.addInventoryItem(form, bindingResult, redirectAttributes);
        
        assertEquals("redirect:/inventory/input", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("inventoryForm"));
    }

    @Test
    void addInventoryItem_shouldHandleBackendException() {
        InventoryForm form = new InventoryForm();
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(false);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(backendApiClient.post(anyString(), any(), eq(InventoryItemDto.class))).thenThrow(new RuntimeException("Error"));
        
        String view = controller.addInventoryItem(form, bindingResult, redirectAttributes);
        
        assertEquals("redirect:/inventory/input", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("errorToast"));
    }

    @Test
    void bookOutInventoryItem_shouldRedirectOnSuccess() {
        UUID id = UUID.randomUUID();
        de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form = new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
        form.setAmount(10.0);
        form.setVersion(1L);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(false);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(backendApiClient.post(anyString(), any(), eq(Void.class))).thenReturn(null);

        String view = controller.bookOutInventoryItem(id, form, bindingResult, redirectAttributes, "/inventory/all");
        
        assertEquals("redirect:/inventory/all", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("successToast"));
    }

    @Test
    void bookOutInventoryItem_shouldHandleBackendException() {
        UUID id = UUID.randomUUID();
        de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form = new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm();
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.hasErrors()).thenReturn(false);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(backendApiClient.post(anyString(), any(), eq(Void.class))).thenThrow(new RuntimeException("Update error"));

        String view = controller.bookOutInventoryItem(id, form, bindingResult, redirectAttributes, "/inventory/all");
        
        assertEquals("redirect:/inventory/all", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("errorToast"));
    }
}