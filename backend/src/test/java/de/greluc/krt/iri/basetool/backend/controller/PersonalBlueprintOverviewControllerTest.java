package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewOwnerDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.PersonalBlueprintOverviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link PersonalBlueprintOverviewController}: page-envelope wrapping and
 * delegation. The {@code @PreAuthorize("@ownerScopeService.canAccessBlueprintOverview()")} gate is
 * a declarative Spring concern and is exercised through {@code OwnerScopeServiceTest} (the
 * predicate) rather than here, matching the unit-test style of {@code
 * PersonalBlueprintControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class PersonalBlueprintOverviewControllerTest {

  @Mock private PersonalBlueprintOverviewService service;
  @InjectMocks private PersonalBlueprintOverviewController controller;

  @Test
  void list_wrapsServicePageIntoPageResponse() {
    Page<BlueprintOverviewEntryDto> page =
        new PageImpl<>(
            List.of(new BlueprintOverviewEntryDto("aurora", "Aurora MR", 3L)),
            PageRequest.of(0, 50, Sort.by("productName")),
            1);
    when(service.listAvailableBlueprints(any())).thenReturn(page);

    PageResponse<BlueprintOverviewEntryDto> result =
        controller.listAvailableBlueprints(0, 50, null);

    assertEquals(1, result.totalElements());
    assertEquals("Aurora MR", result.content().get(0).productName());
    assertEquals(3L, result.content().get(0).ownerCount());
    verify(service).listAvailableBlueprints(any());
  }

  @Test
  void owners_relaysProductKeyToService() {
    when(service.listOwnersForProduct("aurora"))
        .thenReturn(List.of(new BlueprintOverviewOwnerDto("Alpha")));

    List<BlueprintOverviewOwnerDto> result = controller.listOwners("aurora");

    assertEquals(1, result.size());
    assertEquals("Alpha", result.get(0).ownerName());
    verify(service).listOwnersForProduct("aurora");
  }
}
