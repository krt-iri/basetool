package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialCategoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
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
 * MVC-level rendering checks for the {@code /materials} category-listing page and the {@code
 * /materials/{id}} detail page.
 *
 * <p>Originally added because Thymeleaf 3.1's JavaScript-inline mechanism truncates the rest of the
 * surrounding {@code <script>} block as soon as it serialises a Java {@code List}/{@code
 * Collection} into a JS context — the substituted {@code ["Aluminum"]} value swallows every event
 * after it. That truncation killed the {@code window.krtEvents.on('click', 'materials-toggle-kind',
 * …)} registration further down in the script, so clicking a category header no longer expanded the
 * materials grid. The autocomplete name list now lives in a {@code <datalist>} sibling element
 * instead, which means the script no longer needs to inline a {@code List}. The detail page carried
 * the same broken pattern (terminal names list); the second test in this class pins the
 * post-datalist filter binding there.
 *
 * <p>The assertions below pin both halves of the contract: the post-datalist binding survives into
 * the rendered HTML, and the page ends with the closing {@code </html>} tag (i.e. Thymeleaf did not
 * abort mid-render). A regression that re-introduces the broken inline pattern would fail the
 * second assertion long before any human notices the missing click handler.
 */
@SpringBootTest
class MaterialsPageControllerMvcTest {

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

  @Test
  @WithMockUser
  void listMaterials_rendersCategoryToggleBindingAndCompletesScript() throws Exception {
    MaterialPriceOverviewDto dto =
        new MaterialPriceOverviewDto(
            UUID.randomUUID(),
            "Aluminum",
            new MaterialCategoryDto(UUID.randomUUID(), "Mineral", 0L),
            false,
            false,
            false,
            new BigDecimal("5.0"),
            new BigDecimal("7.0"));
    PageResponse<MaterialPriceOverviewDto> pageResponse =
        new PageResponse<>(List.of(dto), 0, 10000, 1, 1, List.of());

    when(backendApiClient.get(
            eq("/api/v1/materials/prices-overview?size=10000&sort=name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(pageResponse);

    mockMvc
        .perform(get("/materials"))
        .andExpect(status().isOk())
        // The category-expand click handler must be registered. If the surrounding <script> block
        // gets truncated mid-render (the bug this test exists to prevent), this line is the first
        // thing that disappears from the output.
        .andExpect(
            content()
                .string(containsString("window.krtEvents.on('click', 'materials-toggle-kind'")))
        // Rendering must not abort mid-stream. A truncated response stops at the substituted
        // expression value (e.g. ["Aluminum"]) and never emits the closing </body></html> pair.
        .andExpect(content().string(containsString("</body>")))
        .andExpect(content().string(containsString("</html>")))
        // The datalist that carries the autocomplete names must have rendered with the material as
        // an option — that's the data source the surrounding script now reads from.
        .andExpect(content().string(containsString("<datalist id=\"materialNames-data\">")))
        .andExpect(content().string(containsString("<option value=\"Aluminum\">")));
  }

  /**
   * Mirror of {@link #listMaterials_rendersCategoryToggleBindingAndCompletesScript()} for the
   * per-material detail page ({@code /materials/{id}}). Pre-fix, {@code material-detail.html}
   * carried the same broken inline pattern (now {@code const terminalNames = …}) at the top of its
   * script, so the {@code 'material-detail-filter-terminals'} delegated binding registered at the
   * tail of the script — plus the surrounding sortable-column handler — silently never wired. The
   * datalist workaround moves the terminal names into {@code <datalist id="terminalNames-data">}
   * next to the filter input. This test pins both halves of the same contract: the post-datalist
   * binding key is in the rendered HTML, and the response actually contains {@code </html>}.
   */
  @Test
  @WithMockUser
  void getMaterialDetail_ShouldRenderFilterBinding_AfterDatalist() throws Exception {
    UUID id = UUID.randomUUID();
    MaterialDto material =
        new MaterialDto(
            id,
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
            0L);
    MaterialPriceDto priceDto =
        new MaterialPriceDto(
            UUID.randomUUID(),
            "Area18",
            new BigDecimal("5.0"),
            new BigDecimal("7.0"),
            100,
            200,
            true,
            true);
    PageResponse<MaterialPriceDto> pricesPage =
        new PageResponse<>(List.of(priceDto), 0, 1000, 1, 1, List.of());

    when(backendApiClient.get(eq("/api/v1/materials/" + id), eq(MaterialDto.class)))
        .thenReturn(material);
    when(backendApiClient.get(
            eq("/api/v1/materials/" + id + "/prices?size=1000&sort=terminal.name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(pricesPage);

    mockMvc
        .perform(get("/materials/" + id))
        .andExpect(status().isOk())
        // Post-datalist binding key: the tail of the script registers the live filter on the
        // terminal table. If the inline-list bug re-appears, this string disappears from output.
        .andExpect(content().string(containsString("'material-detail-filter-terminals'")))
        // Rendering completion marker: the truncation aborts before </body></html>.
        .andExpect(content().string(containsString("</body>")))
        .andExpect(content().string(containsString("</html>")))
        // The datalist that carries the terminal names must be rendered with the price's terminal
        // as an option — that's the data source the surrounding script now reads from.
        .andExpect(content().string(containsString("id=\"terminalNames-data\"")))
        .andExpect(content().string(containsString("value=\"Area18\"")));
  }
}
