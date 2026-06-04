/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.frontend.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
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

  /**
   * Total attempts for the Keycloak login flow before giving up. The OIDC round-trip is the suite's
   * documented high-risk flakiness class (issuer timing under CI load — see {@code
   * docs/E2E_TESTING_PLAN.md}): when the runner is simultaneously building the stack, driving a
   * browser and running the JVM, Keycloak occasionally stalls past the post-credential redirect's
   * 30 s wait, surfacing as a {@link TimeoutError} on an otherwise-correct login. Retrying the
   * whole flow on a freshly re-navigated page absorbs that transient stall; a genuinely broken
   * login still fails every attempt and propagates, so the retry hardens against timing without
   * masking real breakage. Three keeps the worst case bounded well inside the job's 45-minute
   * budget.
   */
  private static final int LOGIN_MAX_ATTEMPTS = 3;

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
   * origin, retrying the whole flow up to {@link #LOGIN_MAX_ATTEMPTS} times when an attempt times
   * out. A timed-out {@code waitForURL} means Keycloak never issued the redirect back and so set no
   * authenticated session, which makes the retry safe and effective: re-navigating to the
   * authorization endpoint on the same page reliably shows the form again (and aborts any
   * navigation the stalled attempt left in flight), so a fresh attempt clears the transient
   * issuer-timing stall. Because login is read-only this never re-runs a destructive mutation —
   * unlike a whole-test retry — and every login in the suite gets the same resilience: the {@link
   * #authenticatedStorageState} callers funnel through here, as do the multi-user tests that drive
   * {@code login} directly. The final attempt's {@link TimeoutError} propagates unchanged, so a
   * genuinely broken login is not masked.
   *
   * @param page the page to drive
   * @param baseUrl the frontend origin
   * @param username Keycloak username
   * @param password Keycloak password
   * @throws TimeoutError if the login does not complete within {@link #LOGIN_MAX_ATTEMPTS} attempts
   */
  static void login(Page page, String baseUrl, String username, String password) {
    // Attempts 1..N-1 retry on a timeout; the final attempt runs uncaught so a persistent
    // failure propagates rather than being swallowed. Structuring it this way keeps the loop
    // condition the genuine bound (instead of an in-body throw that the linter — rightly —
    // flags as making the condition always true).
    for (int attempt = 1; attempt < LOGIN_MAX_ATTEMPTS; attempt++) {
      try {
        attemptLogin(page, baseUrl, username, password);
        return;
      } catch (TimeoutError timeout) {
        System.out.printf(
            "[E2E][login] attempt %d/%d did not reach %s in time; retrying with a fresh"
                + " navigation%n",
            attempt, LOGIN_MAX_ATTEMPTS, baseUrl);
      }
    }
    attemptLogin(page, baseUrl, username, password);
  }

  /**
   * Performs a single Keycloak login attempt: navigates to the authorization endpoint (which aborts
   * any navigation a previous attempt left in flight and re-renders the default-theme form), fills
   * and submits the credentials, then waits up to 30 s for the redirect back to the frontend
   * origin. Extracted from {@link #login} so the retry loop there can re-run the complete flow on a
   * clean navigation rather than from a half-redirected page.
   *
   * @param page the page to drive
   * @param baseUrl the frontend origin
   * @param username Keycloak username
   * @param password Keycloak password
   * @throws TimeoutError if the form never appears or the redirect back to {@code baseUrl} does not
   *     arrive within 30 s
   */
  private static void attemptLogin(Page page, String baseUrl, String username, String password) {
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
   * Clicks a submit control that can be covered by the {@code position: fixed} global footer
   * ({@code .krt-footer}, introduced with the das-kartell design system). A long form's bottom
   * submit button sits behind the footer, so a coordinate click is intercepted and times out. The
   * alternatives each failed on at least one engine — {@code dispatchEvent} is untrusted, {@code
   * press("Enter")} does not activate these submits on WebKit, and {@code requestSubmit()} aborted
   * the app's submit. So this drops the footer out of the way (it is irrelevant to the submit flow,
   * and the page-side {@code evaluate} runs fine under the strict CSP — unlike string-predicate
   * {@code eval}), then performs a normal, trusted click that submits with full validation,
   * consistently across Chromium, Firefox and WebKit.
   *
   * @param submit the submit control (a {@code <button type="submit">}) to click
   */
  static void clickSubmitClearingFooter(Locator submit) {
    submit
        .page()
        .evaluate(
            "() => { const f = document.querySelector('.krt-footer'); if (f) { f.style.display ="
                + " 'none'; } }");
    submit.click();
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
