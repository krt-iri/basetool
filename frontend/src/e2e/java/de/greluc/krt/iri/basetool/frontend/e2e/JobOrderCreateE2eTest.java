package de.greluc.krt.iri.basetool.frontend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.SelectOption;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Functional flow: create a Job Order through the UI and verify it appears in the order list.
 *
 * <p>Job Orders are cross-staffel (no OrgUnit-scope filter), but the create form's {@code
 * requestingOrgUnitId} dropdown is populated from the caller's memberships and its material
 * dropdown only lists {@code isJobOrder=true} materials — so {@link BackendSeeder} seeds the
 * IRIDIUM membership and one job-order material in {@link #setUp()}. Uses the {@code
 * order-material-*} / {@code order-submit} hooks on the create form and {@code order-row} on the
 * index, with the reused authenticated session from {@link E2eSupport#authenticatedStorageState}.
 */
@Tag("e2e")
class JobOrderCreateE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String IRIDIUM_ID = "00000000-0000-0000-0000-000000000001";

  private static Playwright playwright;
  private static Browser browser;

  /**
   * Launches the browser and, for the ephemeral stack, seeds the membership and guarantees at least
   * one {@code isJobOrder} material exists for the create-form dropdown. The specific id is not
   * retained: the frontend caches the job-order material list ({@code getCached}), so in the shared
   * stack the dropdown may show a material seeded by another test rather than this one — the test
   * therefore selects whatever the dropdown offers.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(USERNAME, PASSWORD);
      seeder.ensureJobOrderMaterial(USERNAME, PASSWORD, "E2E Job Material");
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
   * Fills and submits the job-order create form (requesting org-unit, contact handle, one material
   * row) and asserts an order then appears in the order list.
   */
  @Test
  void createsAJobOrderThroughTheUi() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + "/orders/create");
        page.locator("#requestingOrgUnitId").selectOption(IRIDIUM_ID);
        page.locator("#handle").fill("E2E Contact");
        // Select whatever material the (frontend-cached) dropdown offers — see setUp().
        page.getByTestId("order-material-select").selectOption(new SelectOption().setIndex(1));
        page.getByTestId("order-material-amount").fill("100");
        page.getByTestId("order-submit").click();
        page.waitForLoadState();

        // The created order must appear in the list (fresh ephemeral DB => exactly one).
        page.navigate(baseUrl + "/orders");
        assertThat(page.getByTestId("order-row").first()).isVisible();
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "joborder-create");
        throw failure;
      }
    }
  }
}
