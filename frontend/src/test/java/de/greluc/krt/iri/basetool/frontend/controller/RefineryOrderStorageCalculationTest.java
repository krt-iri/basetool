package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryOrderStoreForm;
import de.greluc.krt.iri.basetool.frontend.model.form.RefineryOrderStoreItemForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class RefineryOrderStorageCalculationTest {

  @Mock private BackendApiClient backendApiClient;

  @Mock private RoleHierarchy roleHierarchy;

  @InjectMocks private RefineryOrderPageController controller;

  @Mock private OidcUser oidcUser;

  private Model model;

  @BeforeEach
  void setUp() {
    model = new ExtendedModelMap();
  }

  @Test
  void testStoreFormCalculationForPiece() {
    UUID orderId = UUID.randomUUID();
    MaterialDto pieceMaterial =
        new MaterialDto(
            UUID.randomUUID(),
            1,
            "PieceMat",
            "TYPE",
            "PIECE",
            null,
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            0L);
    RefineryGoodDto good =
        new RefineryGoodDto(UUID.randomUUID(), pieceMaterial, 100, pieceMaterial, 15, 100, null);
    RefineryOrderDto orderDto =
        new RefineryOrderDto(
            orderId,
            null,
            null,
            null,
            null,
            60L,
            0.0,
            0d,
            0d,
            0d,
            null,
            List.of(good),
            RefineryOrderStatus.OPEN,
            0L);

    when(backendApiClient.get(eq("/api/v1/refinery-orders/" + orderId), any(Class.class)))
        .thenReturn(orderDto);
    when(oidcUser.getSubject()).thenReturn(UUID.randomUUID().toString());

    controller.viewOrderDetail(orderId, model, oidcUser);

    RefineryOrderStoreForm storeForm = (RefineryOrderStoreForm) model.getAttribute("storeForm");
    assertNotNull(storeForm);
    assertEquals(1, storeForm.getItems().size());
    RefineryOrderStoreItemForm item = storeForm.getItems().get(0);

    assertEquals(15.0, item.getAmount());
    assertEquals("PIECE", item.getQuantityType());
    assertTrue(item.getAmountFixed());
  }

  @Test
  void testStoreFormCalculationForScu() {
    UUID orderId = UUID.randomUUID();
    MaterialDto scuMaterial =
        new MaterialDto(
            UUID.randomUUID(),
            2,
            "ScuMat",
            "TYPE",
            "SCU",
            null,
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            0L);
    RefineryGoodDto good =
        new RefineryGoodDto(UUID.randomUUID(), scuMaterial, 100, scuMaterial, 1234, 100, null);
    RefineryOrderDto orderDto =
        new RefineryOrderDto(
            orderId,
            null,
            null,
            null,
            null,
            60L,
            0.0,
            0d,
            0d,
            0d,
            null,
            List.of(good),
            RefineryOrderStatus.OPEN,
            0L);

    when(backendApiClient.get(eq("/api/v1/refinery-orders/" + orderId), any(Class.class)))
        .thenReturn(orderDto);

    SystemSettingDto settingDto = new SystemSettingDto("refinery.rounding.mode", "HALF_UP", 0L);
    when(backendApiClient.get(eq("/api/v1/settings/refinery.rounding.mode"), any(Class.class)))
        .thenReturn(settingDto);
    when(oidcUser.getSubject()).thenReturn(UUID.randomUUID().toString());

    controller.viewOrderDetail(orderId, model, oidcUser);

    RefineryOrderStoreForm storeForm = (RefineryOrderStoreForm) model.getAttribute("storeForm");
    assertNotNull(storeForm);
    assertEquals(1, storeForm.getItems().size());
    RefineryOrderStoreItemForm item = storeForm.getItems().get(0);

    // 1234 / 100.0 = 12.34
    assertEquals(12.34, item.getAmount());
    assertEquals("SCU", item.getQuantityType());
    assertTrue(item.getAmountFixed());
  }
}
