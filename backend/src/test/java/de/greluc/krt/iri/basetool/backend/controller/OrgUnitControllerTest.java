package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.backend.service.OrgUnitMembershipService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Thin delegation tests for {@link OrgUnitController}. The endpoint is a single-line passthrough to
 * {@link OrgUnitMembershipService#listAllActiveOptions} — the resolver / sort behaviour is pinned
 * by {@link de.greluc.krt.iri.basetool.backend.service.OrgUnitMembershipServiceTest}, so this class
 * only verifies the wiring (handler exists, response shape preserved, no surprise filtering).
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitControllerTest {

  @Mock private OrgUnitMembershipService orgUnitMembershipService;

  @InjectMocks private OrgUnitController controller;

  @Test
  void listActiveOrgUnits_delegatesToService() {
    OrgUnitMembershipOptionDto option =
        new OrgUnitMembershipOptionDto(UUID.randomUUID(), "IRIDIUM", "IRI", OrgUnitKind.SQUADRON);
    when(orgUnitMembershipService.listAllActiveOptions()).thenReturn(List.of(option));

    List<OrgUnitMembershipOptionDto> result = controller.listActiveOrgUnits();

    assertEquals(1, result.size());
    assertSame(option, result.getFirst());
    verify(orgUnitMembershipService).listAllActiveOptions();
  }

  @Test
  void listActiveOrgUnits_emptyService_returnsEmptyList() {
    when(orgUnitMembershipService.listAllActiveOptions()).thenReturn(List.of());

    assertTrue(controller.listActiveOrgUnits().isEmpty());
  }
}
