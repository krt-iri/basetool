package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.BlueprintService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Unit tests for {@link BlueprintController}. */
@ExtendWith(MockitoExtension.class)
class BlueprintControllerTest {

  @Mock private BlueprintService blueprintService;
  @InjectMocks private BlueprintController blueprintController;

  @Test
  void getBlueprints_relaysFilterAndWrapsInPageResponse() {
    BlueprintDto dto = minimalDto();
    when(blueprintService.getBlueprints(eq("omni"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dto)));

    PageResponse<BlueprintDto> response = blueprintController.getBlueprints("omni", 0, 10, null);

    assertEquals(1, response.content().size());
    assertEquals("Omnisky", response.content().get(0).outputName());
  }

  @Test
  void getBlueprints_rejectsNonWhitelistedSortField() {
    // PaginationUtil enforces the sort whitelist before the service is consulted.
    assertThrows(
        IllegalArgumentException.class,
        () -> blueprintController.getBlueprints(null, 0, 10, "maliciousField"));
  }

  private static BlueprintDto minimalDto() {
    return new BlueprintDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BP",
        "Omnisky",
        540,
        false,
        2,
        1,
        "4.8",
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        0L);
  }
}
