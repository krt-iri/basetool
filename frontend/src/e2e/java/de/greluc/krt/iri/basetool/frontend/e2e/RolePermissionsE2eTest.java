package de.greluc.krt.iri.basetool.frontend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Role-permission flow: the Job-Order handover control on {@code orders-detail.html} is gated by
 * {@code sec:authorize="hasAnyRole('LOGISTICIAN', 'OFFICER', 'ADMIN')"}. A plain {@code Squadron
 * Member} must NOT see it; an {@code Officer} must.
 *
 * <p>This is the first flow to exercise the {@code test-member} and {@code test-officer} realm
 * users and the multi-user login pattern — one fresh Keycloak session per role, each in its own
 * browser context (the suite's {@code authenticatedStorageState} writes a single fixed path, so
 * per-user isolation uses separate contexts logging in directly instead). It relies on JUnit
 * running test classes sequentially, so the IRIDIUM home assigned here is not clobbered by a
 * cross-Staffel class.
 */
@Tag("e2e")
class RolePermissionsE2eTest {

  /** Provisions (or, in staging mode, targets) the shared stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String ADMIN_USER = System.getProperty("e2e.username", "test-admin");
  private static final String ADMIN_PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String OFFICER_USER = "test-officer";
  private static final String OFFICER_PASSWORD = "test-officer-pw";
  private static final String MEMBER_USER = "test-member";
  private static final String MEMBER_PASSWORD = "test-member-pw";
  private static final String IRIDIUM_ID = "00000000-0000-0000-0000-000000000001";

  private static Playwright playwright;
  private static Browser browser;
  private static String jobOrderId;

  /**
   * Launches the browser and, for the ephemeral stack, homes the admin/officer/member test users in
   * IRIDIUM and seeds a job order whose detail page both roles can open.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(ADMIN_USER, ADMIN_PASSWORD);
      // Materialise (first login syncs the app_user row) and home both non-admin roles in IRIDIUM,
      // so their session has an org context when they open the order detail page.
      seeder.assignStaffelMembership(
          ADMIN_USER,
          ADMIN_PASSWORD,
          seeder.getUserId(OFFICER_USER, OFFICER_PASSWORD),
          IRIDIUM_ID,
          false,
          false);
      seeder.assignStaffelMembership(
          ADMIN_USER,
          ADMIN_PASSWORD,
          seeder.getUserId(MEMBER_USER, MEMBER_PASSWORD),
          IRIDIUM_ID,
          false,
          false);
      String materialId =
          seeder.ensureJobOrderMaterial(ADMIN_USER, ADMIN_PASSWORD, "E2E Role Material");
      jobOrderId =
          seeder.createJobOrder(
              ADMIN_USER, ADMIN_PASSWORD, IRIDIUM_ID, "E2E Role Order", materialId, 700, 100.0);
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
   * A plain Squadron Member opens the order but the role-gated handover control is not rendered.
   */
  @Test
  void squadronMemberDoesNotSeeTheHandoverControl() {
    assertHandoverControlVisibility(MEMBER_USER, MEMBER_PASSWORD, false);
  }

  /** An Officer opens the same order and sees the handover control. */
  @Test
  void officerSeesTheHandoverControl() {
    assertHandoverControlVisibility(OFFICER_USER, OFFICER_PASSWORD, true);
  }

  /**
   * Logs in as the given user in a fresh context, opens the seeded order, and asserts whether the
   * {@code order-handover-open} control is present. {@code nav-orders} is asserted first as a
   * locale-independent proof that the authenticated detail page actually rendered (so an absent
   * handover control means "role-gated away", not "page failed to load").
   *
   * @param user the Keycloak username to log in as
   * @param password the Keycloak password
   * @param expectedVisible whether the handover control should be visible for this role
   */
  private void assertHandoverControlVisibility(
      String user, String password, boolean expectedVisible) {
    String baseUrl = STACK.baseUrl();
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
      Page page = context.newPage();
      try {
        E2eSupport.login(page, baseUrl, user, password);
        page.navigate(baseUrl + "/orders/" + jobOrderId);
        page.waitForLoadState();
        assertThat(page.getByTestId("nav-orders")).isVisible();
        if (expectedVisible) {
          assertThat(page.getByTestId("order-handover-open")).isVisible();
        } else {
          assertThat(page.getByTestId("order-handover-open")).hasCount(0);
        }
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "role-handover-" + user);
        throw failure;
      }
    }
  }
}
