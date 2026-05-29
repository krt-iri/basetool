package de.greluc.krt.iri.basetool.frontend.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared helpers for the E2E suite: launching Chromium, driving the Keycloak login form, and
 * reusing one authenticated session across the functional flow tests.
 */
final class E2eSupport {

  private E2eSupport() {}

  /**
   * Launches a headless Chromium. For the ephemeral local stack the {@code host.docker.internal}
   * issuer host is remapped to the loopback (Keycloak is published on 127.0.0.1); against an
   * external deployment ({@code !managesStack}) the remap is off. Override with {@code
   * -De2e.hostResolverRules}.
   *
   * @param playwright the Playwright entry point
   * @param managesStack whether an ephemeral local stack is in play (enables the remap)
   * @return a launched headless Chromium browser
   */
  static Browser launchChromium(Playwright playwright, boolean managesStack) {
    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
    String resolverRules =
        System.getProperty(
            "e2e.hostResolverRules", managesStack ? "MAP host.docker.internal 127.0.0.1" : "");
    if (!resolverRules.isBlank()) {
      options.setArgs(List.of("--host-resolver-rules=" + resolverRules));
    }
    return playwright.chromium().launch(options);
  }

  /**
   * Drives the Keycloak default-theme login form and waits for the redirect back to the frontend
   * origin.
   *
   * @param page the page to drive
   * @param baseUrl the frontend origin
   * @param username Keycloak username
   * @param password Keycloak password
   */
  static void login(Page page, String baseUrl, String username, String password) {
    page.navigate(baseUrl + "/oauth2/authorization/keycloak");
    page.waitForSelector("#username");
    page.fill("#username", username);
    page.fill("#password", password);
    page.click("#kc-login");
    page.waitForURL(
        url -> url.startsWith(baseUrl), new Page.WaitForURLOptions().setTimeout(30_000));
  }

  /**
   * Logs in through Keycloak and returns the path to a saved Playwright storageState, so subsequent
   * contexts in the same test class can start already authenticated instead of re-running the OIDC
   * flow. Each call performs a fresh login (its own frontend session) — the result is deliberately
   * NOT memoised across test classes, so flows stay isolated and never share a mutated session.
   *
   * @param browser the test class's browser
   * @param baseUrl the frontend origin
   * @param username Keycloak username
   * @param password Keycloak password
   * @return path to the storageState JSON for {@code newContext(...setStorageStatePath(...))}
   */
  static Path authenticatedStorageState(
      Browser browser, String baseUrl, String username, String password) {
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
      Page page = context.newPage();
      login(page, baseUrl, username, password);
      Path path = Path.of("build", "e2e", "auth-state.json");
      Files.createDirectories(path.getParent());
      context.storageState(new BrowserContext.StorageStateOptions().setPath(path));
      return path;
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist authenticated storageState", e);
    }
  }

  /**
   * Best-effort failure diagnostics: writes a full-page screenshot and the page HTML under {@code
   * build/e2e/<label>-failure.*} and prints the current URL, so a CI run can see what the browser
   * was showing when a flow failed.
   *
   * @param page the page at the point of failure
   * @param label short prefix for the artifact filenames
   */
  static void dump(Page page, String label) {
    try {
      Path dir = Path.of("build", "e2e");
      Files.createDirectories(dir);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(dir.resolve(label + "-failure.png"))
              .setFullPage(true));
      Files.writeString(dir.resolve(label + "-failure.html"), page.content());
      System.out.printf("[E2E][FAIL] %s url=%s%n", label, page.url());
    } catch (RuntimeException | IOException e) {
      System.out.println("[E2E][FAIL] diagnostics dump failed: " + e);
    }
  }
}
