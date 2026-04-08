package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MemberManagementControllerTest {

    @Test
    void deleteMember_ShouldRedirectAndAddSuccessToast() {
        // Arrange
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MemberManagementController controller = new MemberManagementController(backendApiClient);
        UUID userId = UUID.randomUUID();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        // Act
        String view = controller.deleteMember(userId, redirectAttributes);

        // Assert
        verify(backendApiClient).delete("/api/v1/users/" + userId, Void.class);
        assertEquals("redirect:/members", view);
        assertEquals("success.user.delete", redirectAttributes.getFlashAttributes().get("successToast"));
    }

    @Test
    void deleteMember_OnFailure_ShouldRedirectAndAddErrorToast() {
        // Arrange
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        MemberManagementController controller = new MemberManagementController(backendApiClient);
        UUID userId = UUID.randomUUID();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        doThrow(new RuntimeException("API Error")).when(backendApiClient).delete(anyString(), any());

        // Act
        String view = controller.deleteMember(userId, redirectAttributes);

        // Assert
        verify(backendApiClient).delete("/api/v1/users/" + userId, Void.class);
        assertEquals("redirect:/members", view);
        assertEquals("error.user.delete", redirectAttributes.getFlashAttributes().get("errorToast"));
    }
}
