package de.greluc.krt.iri.basetool.frontend.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared helpers for the E2E suite: launching the configured browser (Chromium / Firefox / WebKit),
 * driving the Keycloak login form, and reusing one authenticated session across the functional flow
 * tests.
 */
final class E2eSupport {

  private E2eSupport() {}

  /**
   * Launches a headless browser of the engine named by the {@code e2e.browser} system property —
   * {@code chromium} (default), {@code firefox}, or {@code webkit}. This is the single seam the
   * test classes use, so the whole suite runs against any one engine per JVM (a CI matrix fans the
   * three out in parallel).
   *
   * <p>For the ephemeral local stack the Keycloak issuer host {@code host.docker.internal} must
   * resolve to the loopback (the stack publishes Keycloak on 127.0.0.1). Each engine needs a
   * different mechanism, all gated on {@code managesStack}:
   *
   * <ul>
   *   <li><b>Chromium</b> — the {@code --host-resolver-rules} launch arg (override via {@code
   *       -De2e.hostResolverRules}).
   *   <li><b>Firefox</b> — the {@code network.dns.localDomains} preference.
   *   <li><b>WebKit</b> — no launch-level override exists; it relies on the OS hosts file mapping
   *       {@code host.docker.internal} to 127.0.0.1 (CI adds the entry — see {@code
   *       docs/E2E_TESTING_PLAN.md}; on a workstation it must be added manually).
   * </ul>
   *
   * <p>Against an external deployment ({@code !managesStack}) no remap is applied for any engine —
   * the real hostname resolves normally.
   *
   * @param playwright the Playwright entry point
   * @param managesStack whether an ephemeral local stack is in play (enables the issuer-host remap)
   * @return a launched headless browser of the configured engine
   */
  static Browser launchBrowser(Playwright playwright, boolean managesStack) {
    String engine = System.getProperty("e2e.browser", "chromium").toLowerCase(Locale.ROOT);
    return switch (engine) {
      case "chromium" -> launchChromium(playwright, managesStack);
      case "firefox" -> launchFirefox(playwright, managesStack);
      case "webkit" -> launchWebkit(playwright, managesStack);
      default ->
          throw new IllegalArgumentException(
              "Unknown e2e.browser '" + engine + "' (expected chromium, firefox or webkit)");
    };
  }

  /**
   * Launches headless Chromium, remapping {@code host.docker.internal} to the loopback via {@code
   * --host-resolver-rules} for the ephemeral stack (override with {@code -De2e.hostResolverRules}).
   *
   * @param playwright the Playwright entry point
   * @param managesStack whether the ephemeral stack is in play (enables the remap)
   * @return a launched headless Chromium browser
   */
  private static Browser launchChromium(Playwright playwright, boolean managesStack) {
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
   * Launches headless Firefox, resolving {@code host.docker.internal} to the loopback via the
   * {@code network.dns.localDomains} preference for the ephemeral stack (Firefox has no {@code
   * --host-resolver-rules} equivalent).
   *
   * @param playwright the Playwright entry point
   * @param managesStack whether the ephemeral stack is in play (enables the local-domain remap)
   * @return a launched headless Firefox browser
   */
  private static Browser launchFirefox(Playwright playwright, boolean managesStack) {
    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
    if (managesStack) {
      options.setFirefoxUserPrefs(Map.of("network.dns.localDomains", "host.docker.internal"));
    }
    return playwright.firefox().launch(options);
  }

  /**
   * Launches headless WebKit. WebKit has neither a {@code --host-resolver-rules} arg nor a DNS
   * preference, so for the ephemeral stack it relies on the OS hosts file mapping {@code
   * host.docker.internal} to 127.0.0.1; this fails fast with an actionable message when that
   * mapping is absent rather than surfacing an opaque "Could not connect to server" mid-flow.
   *
   * @param playwright the Playwright entry point
   * @param managesStack whether the ephemeral stack is in play (requires the hosts-file mapping)
   * @return a launched headless WebKit browser
   */
  private static Browser launchWebkit(Playwright playwright, boolean managesStack) {
    if (managesStack) {
      requireHostDockerInternalOnLoopback();
    }
    return playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  /**
   * Verifies that {@code host.docker.internal} resolves to the loopback — the precondition for
   * WebKit to reach the ephemeral stack's Keycloak. The OS resolver (and therefore WebKit) sees
   * whatever this check sees, so an actionable error here pre-empts an opaque connection failure
   * mid-flow.
   *
   * @throws IllegalStateException if the host does not resolve, or resolves to a non-loopback
   *     address
   */
  private static void requireHostDockerInternalOnLoopback() {
    String fix =
        "Add '127.0.0.1 host.docker.internal' to your OS hosts file, or run WebKit against an"
            + " external deployment via E2E_BASE_URL. See docs/E2E_TESTING_PLAN.md.";
    try {
      InetAddress address = InetAddress.getByName("host.docker.internal");
      if (!address.isLoopbackAddress()) {
        throw new IllegalStateException(
            "WebKit cannot reach the ephemeral stack: 'host.docker.internal' resolves to "
                + address.getHostAddress()
                + ", not the loopback. Unlike Chromium/Firefox, WebKit has no launch-level DNS"
                + " override. "
                + fix);
      }
    } catch (UnknownHostException e) {
      throw new IllegalStateException(
          "WebKit cannot reach the ephemeral stack: 'host.docker.internal' does not resolve. "
              + fix,
          e);
    }
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
