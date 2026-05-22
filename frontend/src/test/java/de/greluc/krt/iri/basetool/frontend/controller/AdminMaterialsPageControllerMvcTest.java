package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Regression for the Thymeleaf 3.1 JS-inline truncation bug on {@code /admin/materials}.
 *
 * <p>Pre-fix, the template called {@code /*[[${materials.![name]}]]*&#47;} inside a {@code
 * th:inline="javascript"} script. That inline expression truncated the rest of the script body, so
 * the {@code data-krt-confirm} form delete-confirmation handler binding, the {@code
 * 'admin-materials-update'} delegated event registration, and the row-update {@code fetch} flow
 * never executed. The fix moves the names into a sibling {@code <datalist id="materialNames-data">}
 * read at runtime, while keeping {@code th:inline="javascript"} alive on the script tag so the
 * remaining single-primitive translation lookups (toast keys) still resolve.
 *
 * <p>This test pins both halves: (a) the {@code 'admin-materials-update'} delegated binding string
 * (which lives at the very tail of the script) is in the rendered HTML, and (b) the response ends
 * with the closing {@code </html>} tag — the pre-fix bug truncated the body before that.
 */
@SpringBootTest
class AdminMaterialsPageControllerMvcTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  /**
   * Asserts the {@code 'admin-materials-update'} delegated-event registration (defined AFTER the
   * datalist in the script) appears in the rendered HTML — proof that the Thymeleaf truncation does
   * not strike again.
   */
  @Test
  @WithMockUser(roles = "ADMIN")
  void listMaterials_ShouldRenderUpdateBinding_AfterDatalist() throws Exception {
    MaterialDto material =
        new MaterialDto(
            UUID.randomUUID(),
            1,
            "Aluminum",
            "RAW",
            "SCU",
            "Aluminum description",
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            false,
            0L);
    PageResponse<MaterialDto> materialsPage =
        new PageResponse<>(List.of(material), 0, 1000, 1, 1, Collections.emptyList());

    when(backendApiClient.get(
            eq("/api/v1/materials?size=1000&sort=name,asc"), any(ParameterizedTypeReference.class)))
        .thenReturn(materialsPage);
    when(backendApiClient.get(
            eq("/api/v1/material-categories"), any(ParameterizedTypeReference.class)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/admin/materials"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/materials"))
        .andExpect(content().string(containsString("id=\"materialNames-data\"")))
        .andExpect(content().string(containsString("value=\"Aluminum\"")))
        .andExpect(content().string(containsString("'admin-materials-update'")))
        .andExpect(content().string(containsString("</html>")));
  }
}
