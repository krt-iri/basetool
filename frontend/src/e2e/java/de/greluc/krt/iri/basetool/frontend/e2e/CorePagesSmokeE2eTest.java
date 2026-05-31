package de.greluc.krt.iri.basetool.frontend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Non-destructive smoke subset: log in once and confirm each core page renders the authenticated
 * app shell. Unlike the {@code @Tag("e2e")} flows, this suite is target-agnostic and read-only — it
 * creates and mutates nothing, so it is safe to run against a shared staging deployment.
 *
 * <p>Tagged {@code @Tag("smoke")}: the {@code smokeTest} Gradle task selects it, and it runs
 * against whatever {@link E2eStackExtension} resolves as the base URL — the ephemeral local stack
 * by default, or an external {@code E2E_BASE_URL} (staging) when set, with the user supplied via
 * {@code -Pe2e.username} / {@code -Pe2e.password} (CI secrets).
 *
 * <p>The assertion targets {@code nav-orders}, a sidebar link gated by {@code isAuthenticated()},
 * so its visibility proves the session is authenticated and the page neither errored nor bounced to
 * the identity provider — a stronger signal than {@code nav-missions}, which renders for anonymous
 * visitors too.
 */
@Tag("smoke")
class CorePagesSmokeE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");

  private static Playwright playwright;
  private static Browser browser;
  private static Path storageState;

  /** Launches the browser and captures one authenticated session reused across all page checks. */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    storageState =
        E2eSupport.authenticatedStorageState(browser, STACK.baseUrl(), USERNAME, PASSWORD);
  }

  /** Releases the browser and the Playwright driver process. */
  @AfterAll
  static void tearDown() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /**
   * Navigates to a core page with the authenticated session and asserts the authenticated sidebar
   * renders, proving the page loads for a logged-in user without touching any data.
   *
   * @param path the app-relative path of the core page to load
   */
  @ParameterizedTest(name = "core page {0} loads")
  @ValueSource(strings = {"/", "/missions", "/orders", "/refinery-orders", "/hangar"})
  void corePageLoads(String path) {
    String baseUrl = STACK.baseUrl();
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + path);
        assertThat(page.getByTestId("nav-orders")).isVisible();
      } catch (RuntimeException | AssertionError failure) {
        String slug = path.equals("/") ? "home" : path.substring(1).replace('/', '-');
        E2eSupport.dump(page, "smoke-" + slug);
        throw failure;
      }
    }
  }
}
