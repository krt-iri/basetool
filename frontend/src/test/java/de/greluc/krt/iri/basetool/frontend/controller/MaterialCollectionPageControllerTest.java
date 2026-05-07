package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class MaterialCollectionPageControllerTest {

    @Test
    void viewMaterialCollection_shouldPopulateModelAndReturnTemplate() {
        // Given
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MaterialCollectionPageController controller = new MaterialCollectionPageController(backendApiClient);
        Model model = new ConcurrentModel();
        UUID jobOrderId = UUID.randomUUID();

        List<Map<String, Object>> entries = List.of(Map.of("materialName", "Laranite"));
        List<UserReferenceDto> users = List.of(new UserReferenceDto(UUID.randomUUID(), "user1", "User One", "User One", 1));
        List<LocationReferenceDto> locations = List.of(new LocationReferenceDto(UUID.randomUUID(), "Port Olisar"));

        when(backendApiClient.get(contains("/material-collection"), any(ParameterizedTypeReference.class))).thenReturn(entries);
        when(backendApiClient.get(contains("/users/lookup"), any(ParameterizedTypeReference.class))).thenReturn(users);
        when(backendApiClient.getCached(contains("/locations/lookup"), any(ParameterizedTypeReference.class))).thenReturn(locations);

        // When
        String viewName = controller.viewMaterialCollection(jobOrderId, model);

        // Then
        assertEquals("material-collection", viewName);
        assertEquals(jobOrderId, model.getAttribute("jobOrderId"));
        assertEquals(entries, model.getAttribute("entries"));
        assertEquals(users, model.getAttribute("users"));
        assertEquals(locations, model.getAttribute("locations"));
    }

    @Test
    void viewMaterialCollection_shouldHandleBackendErrorForEntries() {
        // Given
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MaterialCollectionPageController controller = new MaterialCollectionPageController(backendApiClient);
        Model model = new ConcurrentModel();
        UUID jobOrderId = UUID.randomUUID();

        when(backendApiClient.get(contains("/material-collection"), any(ParameterizedTypeReference.class)))
                .thenThrow(new BackendServiceException("Backend error", null, 500));
        when(backendApiClient.get(contains("/users/lookup"), any(ParameterizedTypeReference.class))).thenReturn(List.of());
        when(backendApiClient.getCached(contains("/locations/lookup"), any(ParameterizedTypeReference.class))).thenReturn(List.of());

        // When
        String viewName = controller.viewMaterialCollection(jobOrderId, model);

        // Then
        assertEquals("material-collection", viewName);
        List<?> entries = (List<?>) model.getAttribute("entries");
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void viewMaterialCollection_shouldHandleBackendErrorForUsers() {
        // Given
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MaterialCollectionPageController controller = new MaterialCollectionPageController(backendApiClient);
        Model model = new ConcurrentModel();
        UUID jobOrderId = UUID.randomUUID();

        when(backendApiClient.get(contains("/material-collection"), any(ParameterizedTypeReference.class))).thenReturn(List.of());
        when(backendApiClient.get(contains("/users/lookup"), any(ParameterizedTypeReference.class)))
                .thenThrow(new BackendServiceException("Backend error", null, 500));
        when(backendApiClient.getCached(contains("/locations/lookup"), any(ParameterizedTypeReference.class))).thenReturn(List.of());

        // When
        String viewName = controller.viewMaterialCollection(jobOrderId, model);

        // Then
        assertEquals("material-collection", viewName);
        List<?> users = (List<?>) model.getAttribute("users");
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    void viewMaterialCollection_shouldHandleBackendErrorForLocations() {
        // Given
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MaterialCollectionPageController controller = new MaterialCollectionPageController(backendApiClient);
        Model model = new ConcurrentModel();
        UUID jobOrderId = UUID.randomUUID();

        when(backendApiClient.get(contains("/material-collection"), any(ParameterizedTypeReference.class))).thenReturn(List.of());
        when(backendApiClient.get(contains("/users/lookup"), any(ParameterizedTypeReference.class))).thenReturn(List.of());
        when(backendApiClient.getCached(contains("/locations/lookup"), any(ParameterizedTypeReference.class)))
                .thenThrow(new BackendServiceException("Backend error", null, 500));

        // When
        String viewName = controller.viewMaterialCollection(jobOrderId, model);

        // Then
        assertEquals("material-collection", viewName);
        List<?> locations = (List<?>) model.getAttribute("locations");
        assertNotNull(locations);
        assertTrue(locations.isEmpty());
    }
}
