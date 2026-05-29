package de.greluc.krt.iri.basetool.frontend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.SelectOption;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Functional flow: create a Refinery Order through the UI and verify it appears in the order list.
 *
 * <p>Refinery orders are staffel-scoped (need an OrgUnit membership) and the create form selects a
 * Location, a RefiningMethod and an input Material — so {@link BackendSeeder} seeds the IRIDIUM
 * membership plus those three reference entities in {@link #setUp()}. The first goods row is
 * pre-rendered ({@code #inputMaterialId_0} / {@code #outputQuantity_0}); the owner field is only
 * editable for logisticians and otherwise auto-defaults to the caller.
 */
@Tag("e2e")
@Disabled(
    "Blocked: the refinery-order form's location dropdown lists only refinery-hosting locations"
        + " (LocationService.getRefineryLocations / GET /api/v1/locations/refineries), which are"
        + " UEX-synced — a plain POST /api/v1/locations is not refinery-hosting, so the seeded"
        + " location never appears. Needs UEX-catalog or direct-DB seeding (see"
        + " docs/E2E_TESTING_PLAN.md, Phase 3). The flow + seeding scaffold are kept for then.")
class RefineryOrderCreateE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");

  private static Playwright playwright;
  private static Browser browser;
  private static String locationId;
  private static String methodId;
  private static String materialId;

  /** Launches the browser and, for the ephemeral stack, seeds the membership + reference data. */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchChromium(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(USERNAME, PASSWORD);
      locationId = seeder.createLocation(USERNAME, PASSWORD, "E2E Location");
      methodId = seeder.createRefiningMethod(USERNAME, PASSWORD, "E2E Method");
      materialId = seeder.createRefineryMaterial(USERNAME, PASSWORD, "E2E Refinery Material");
    }
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
   * Fills and submits the refinery-order create form (location, refining method, one input-material
   * good) and asserts a refinery order then appears in the list.
   */
  @Test
  void createsARefineryOrderThroughTheUi() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + "/refinery-orders");
        page.getByTestId("refinery-create-link").click();
        page.waitForURL(url -> url.contains("/refinery-orders/create"));
        page.waitForLoadState();

        // The owner select is editable only for logisticians; pick the first user when enabled,
        // otherwise it auto-defaults to the caller via a hidden field.
        Locator owner = page.locator("#ownerId");
        if (owner.isEnabled()) {
          owner.selectOption(new SelectOption().setIndex(1));
        }
        page.locator("#locationId").selectOption(locationId);
        page.locator("#refiningMethodId").selectOption(methodId);
        page.locator("#inputMaterialId_0").selectOption(materialId);
        page.locator("#outputQuantity_0").fill("100");
        page.getByTestId("refinery-submit").click();
        page.waitForLoadState();

        // The created order must appear in the list (fresh ephemeral DB => exactly one).
        page.navigate(baseUrl + "/refinery-orders");
        assertThat(page.getByTestId("refinery-order-row").first()).isVisible();
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "refinery-create");
        throw failure;
      }
    }
  }
}
