package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class UserProxyControllerTest {

    @Test
    void searchUsers_ShouldCallWebClient() {
        // Arrange
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        UserProxyController controller = new UserProxyController(backendApiClient);
        
        PageResponse<Map<String, Object>> mockPageResponse = new PageResponse<>(Collections.emptyList(), 0, 1000, 0, 0, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(mockPageResponse);

        // Act
        List<Map<String, Object>> result = controller.searchUsers("query");

        // Assert
        assertNotNull(result);
        verify(backendApiClient).get(eq("/api/v1/users/search?query=query&size=1000&sort=username,asc"), any(ParameterizedTypeReference.class));
    }
}
