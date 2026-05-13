package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SuppressWarnings("unchecked")
class ShipDataPageControllerTest {

  @Test
  void testListData_Success() {
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    PageResponse<ManufacturerDto> emptyManufacturerPage =
        new PageResponse<>(Collections.emptyList(), 0, 1000, 0, 1, Collections.emptyList());
    PageResponse<ShipTypeDto> emptyShipTypePage =
        new PageResponse<>(Collections.emptyList(), 0, 1000, 0, 1, Collections.emptyList());

    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(emptyManufacturerPage)
        .thenReturn(emptyShipTypePage);

    ShipDataPageController controller = new ShipDataPageController(backendApiClient);
    Model model = new ConcurrentModel();

    String view = controller.listData(model);

    assertEquals("ship-data", view);
  }

  @Test
  void testResetAllFitted_Success() {
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    ShipDataPageController controller = new ShipDataPageController(backendApiClient);
    RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

    String view = controller.resetAllFitted(redirectAttributes);

    verify(backendApiClient).post("/api/v1/hangar/ships/reset-fitted", null, Void.class);
    verify(redirectAttributes)
        .addFlashAttribute("successToast", "notification.success.ship_unfitted");
    assertEquals("redirect:/ship-data", view);
  }
}
