package de.greluc.krt.iri.basetool.frontend.template;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the reusable design-system component fragments ({@code fragments/components.html}) through
 * the Spring Thymeleaf engine and asserts the produced markup matches the krt-components.css /
 * preview spec. Thymeleaf fragment errors surface only at render time (never at compile/build time),
 * so this test pins button/alert/table down the same way {@code OperationPageControllerMvcTest}
 * happens to cover the alert fragment via the operations list — but here every fragment, including
 * the otherwise-unexercised {@code dataTable} shell and the {@code null}-message self-gating, is
 * driven directly through a test-only harness template.
 */
@SpringBootTest
class ComponentFragmentsRenderTest {

  @Autowired private ITemplateEngine templateEngine;

  // The full application context refuses to start without these collaborators; they are not
  // touched by the engine render itself (see OperationPageControllerMvcTest for the same pattern).
  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

  /**
   * Drives the harness template and asserts each fragment emits its canonical classes/structure:
   * the button carries {@code btn btn-danger} + a submit type, the alert carries the tinted
   * {@code alert alert-danger} with its extra class, an alert fed a {@code null} message renders
   * nothing (no stray {@code alert-success}), and the data table is wrapped responsively with the
   * {@code krt-table} class plus the injected header cells and body rows.
   */
  @Test
  void rendersButtonAlertAndDataTableFragmentsToSpec() {
    Context context = new Context(Locale.ENGLISH);
    context.setVariable("msgKey", "info.delete");
    context.setVariable("rows", List.of("ROW-1"));

    String html = templateEngine.process("component-fragment-harness", context);

    // Button: .btn + variant class, submit type and the resolved label.
    assertThat(html).contains("class=\"btn btn-danger\"");
    assertThat(html).contains("type=\"submit\"");
    assertThat(html).contains(">DELETE<");

    // Alert with a message: tinted alert in the status colour plus the extra utility class.
    assertThat(html).contains("class=\"alert alert-danger mt-2\"");

    // Alert with a null message key self-gates (th:if inside the fragment) -> nothing rendered.
    assertThat(html).doesNotContain("alert-success");

    // Data table: responsive wrapper + krt-table shell + injected head cells and body rows.
    assertThat(html).contains("class=\"table-responsive\"");
    assertThat(html).contains("class=\"krt-table\"");
    assertThat(html).contains(">COL-A<");
    assertThat(html).contains(">ROW-1<");
  }
}
