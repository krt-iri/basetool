package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.service.OwnerScopeService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito-driven tests for {@link MeController}. Two read endpoints exist during the
 * SPEZIALKOMMANDO_PLAN.md §7.2 rename soak: the new canonical {@code GET /active-org-unit} and the
 * deprecated alias {@code GET /active-squadron}. Both pull from the same {@code
 * OwnerScopeService.currentOrgUnitId()} resolver — the test pins the routing + the field-name
 * difference in the two response records.
 */
@ExtendWith(MockitoExtension.class)
class MeControllerTest {

  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private MeController controller;

  @Test
  void getActiveOrgUnit_present_returnsUuid() {
    UUID active = UUID.randomUUID();
    when(ownerScopeService.currentOrgUnitId()).thenReturn(Optional.of(active));

    MeController.ActiveOrgUnitResponse response = controller.getActiveOrgUnit();

    assertEquals(active, response.orgUnitId());
    verify(ownerScopeService).currentOrgUnitId();
  }

  @Test
  void getActiveOrgUnit_empty_returnsNull() {
    when(ownerScopeService.currentOrgUnitId()).thenReturn(Optional.empty());

    assertNull(controller.getActiveOrgUnit().orgUnitId());
  }

  @Test
  void getActiveSquadron_legacyAlias_routesToSameResolver_andPreservesLegacyFieldName() {
    UUID active = UUID.randomUUID();
    when(ownerScopeService.currentOrgUnitId()).thenReturn(Optional.of(active));

    MeController.ActiveSquadronResponse response = controller.getActiveSquadron();

    assertEquals(active, response.squadronId());
    verify(ownerScopeService).currentOrgUnitId();
  }

  @Test
  void getActiveSquadron_legacyAlias_empty_returnsNull() {
    when(ownerScopeService.currentOrgUnitId()).thenReturn(Optional.empty());

    assertNull(controller.getActiveSquadron().squadronId());
  }
}
