package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryLocationType;
import de.greluc.krt.iri.basetool.frontend.model.dto.UexLocationDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Pure-unit test for the {@code /personal-inventory/uex-search} typeahead endpoint exposed by
 * {@link PersonalInventoryPageController}. Verifies the URL composition, default and clamped
 * limits, and the silent fallback to an empty list on backend failures (so the typeahead never
 * shows a stack trace to the user).
 */
@ExtendWith(MockitoExtension.class)
class PersonalInventoryUexSearchTest {

  @Mock private BackendApiClient backendApiClient;

  @InjectMocks private PersonalInventoryPageController controller;

  @Test
  void uexSearch_delegatesToBackend_andDefaultsLimitTo25() {
    // Given
    UexLocationDto dto =
        new UexLocationDto(
            42, PersonalInventoryLocationType.CITY, "Lorville", "Stanton", "Hurston");
    when(backendApiClient.get(
            contains("/api/v1/uex/locations/search?q=lor&limit=25"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(dto));

    // When
    List<UexLocationDto> result = controller.uexSearch("lor", null);

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("Lorville", result.get(0).name());
  }

  @Test
  void uexSearch_clampsLimit_to100() {
    // Given
    when(backendApiClient.get(contains("limit=100"), any(ParameterizedTypeReference.class)))
        .thenReturn(List.of());

    // When
    List<UexLocationDto> result = controller.uexSearch("x", 9999);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void uexSearch_returnsEmptyList_whenBackendThrows() {
    // Given: backend throws unexpectedly; the controller must swallow it.
    when(backendApiClient.get(any(), any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("boom"));

    // When
    List<UexLocationDto> result = controller.uexSearch("anything", 25);

    // Then
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}
