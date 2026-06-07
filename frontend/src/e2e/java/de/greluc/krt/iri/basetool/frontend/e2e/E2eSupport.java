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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
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

  /**
   * Total attempts for {@link #navigate} before a navigation abort is allowed to propagate. Mirrors
   * {@link #LOGIN_MAX_ATTEMPTS}: attempts {@code 1..N-1} retry a transient abort, the {@code N}-th
   * runs uncaught so a persistent failure still surfaces with its real error. Three bounds the rare
   * WebKit-abort retry loop; since the retry fires only on the abort path it never extends a
   * healthy run.
   */
  private static final int NAVIGATE_MAX_ATTEMPTS = 3;

  /**
   * Settle pause, in milliseconds, between a transient navigation abort and the retry. It gives the
   * reset HTTP/2 stream time to tear down so the re-issued GET opens a fresh one. Kept short
   * because the WebKit {@code INTERNAL_ERROR} reset clears almost immediately, and it only ever
   * runs on the abort path.
   */
  private static final int NAVIGATE_RETRY_BACKOFF_MILLIS = 500;

  /**
   * Lowercase substrings that mark a {@code page.navigate(...)} failure as a transient connection
   * reset of the navigation request rather than a genuine page failure (see {@link
   * #isTransientNavigationAbort}): WebKit's HTTP/2 {@code INTERNAL_ERROR} and its {@code
   * frameAbortedNavigation} wrapper, Firefox's {@code NS_BINDING_ABORTED}, Chromium's {@code
   * net::ERR_ABORTED}, and Playwright's own "Navigation interrupted by another one". Deliberately
   * narrow so only a genuine abort is retried and every real error propagates unmasked.
   */
  private static final List<String> NAVIGATION_ABORT_SIGNATURES =
      List.of(
          "internal_error",
          "frameabortednavigation",
          "ns_binding_aborted",
          "err_aborted",
          "interrupted");

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
   * <p>The click is wrapped in {@link #awaitFormPost} so the method only returns once the
   * post-submit navigation (including the redirect it triggers) has settled — see that method for
   * why a bare click would otherwise let a follow-up {@code navigate(...)} abort the in-flight
   * submit on WebKit.
   *
   * @param submit the submit control (a {@code <button type="submit">}) to click
   */
  static void clickSubmitClearingFooter(Locator submit) {
    Page page = submit.page();
    page.evaluate(
        "() => { const f = document.querySelector('.krt-footer'); if (f) { f.style.display ="
            + " 'none'; } }");
    awaitFormPost(page, submit::click);
  }

  /**
   * Runs a submit action that triggers a full-page form POST and blocks until the resulting
   * navigation — <em>including the redirect the POST triggers</em> — has fully settled, so the
   * submit and the page it lands on cannot be dropped by whatever the test does next.
   *
   * <p>Playwright's {@code locator.click()} returns once the click is dispatched — it does NOT wait
   * for any navigation the click starts. The original pattern ({@code click()} then {@code
   * waitForLoadState()}) was racy: when the POST had not yet committed, {@code waitForLoadState()}
   * saw the still-current form document (already in the {@code load} state) and resolved
   * immediately, so the test's next {@code navigate(...)} aborted the in-flight POST and the
   * mutation was silently lost — the flaky, engine-specific "row not visible" failures.
   *
   * <p>Waiting only for the POST's own {@code 3xx} response (an earlier, incomplete fix) is also
   * insufficient: the browser then follows that redirect with a GET for the post-submit document,
   * and a {@code navigate(...)} fired while that GET is still in flight aborts it. WebKit surfaces
   * the aborted navigation as {@code HTTP/2 Error: INTERNAL_ERROR} ({@code frameAbortedNavigation})
   * — the failure this method now guards against.
   *
   * <p>So this waits for the <strong>settled</strong> post-submit document response: the redirect
   * target's GET (the normal Post/Redirect/Get path), or — defensively — a non-{@code 3xx} POST
   * document for a submit that renders its own page without redirecting (which keeps the wait from
   * hanging for a redirect that never comes). Either way the backend mutation has provably landed
   * and no navigation is in flight, so any subsequent {@code navigate(...)} is safe. The {@code
   * isNavigationRequest} and {@code document} guards restrict the match to the top-level
   * navigation, excluding XHR/WebSocket/beacon and subresource requests.
   *
   * @param page the page whose main-frame post-submit navigation to await
   * @param submitAction the action (typically a submit-button click) that starts the form POST
   */
  static void awaitFormPost(Page page, Runnable submitAction) {
    page.waitForResponse(
        response -> {
          Request request = response.request();
          if (!request.isNavigationRequest() || !"document".equals(request.resourceType())) {
            return false;
          }
          // Post/Redirect/Get happy path: the app answers the POST with a 3xx and the browser
          // follows it with a GET for the post-submit document. Waiting for that GET's response —
          // not the POST's 3xx — means the redirect has fully committed, so the caller's next
          // navigate(...) has no in-flight redirect GET to abort.
          if ("GET".equals(request.method())) {
            return true;
          }
          // Defensive: a submit that renders its own document (no redirect) settles on the POST
          // response itself, so accept any non-3xx POST document too rather than hang waiting for
          // a redirect that will never arrive.
          int status = response.status();
          return "POST".equals(request.method()) && (status < 300 || status >= 400);
        },
        new Page.WaitForResponseOptions().setTimeout(15_000),
        submitAction);
  }

  /**
   * Navigates to {@code url}, retrying up to {@link #NAVIGATE_MAX_ATTEMPTS} times when the
   * navigation request is aborted by a transient, engine-level connection reset rather than the
   * target page genuinely failing.
   *
   * <p>This hardens the post-submit list re-load that several flows perform immediately after
   * {@link #awaitFormPost} / {@link #clickSubmitClearingFooter}. {@code awaitFormPost} already
   * blocks until the submit's Post/Redirect/Get has fully settled — so the mutation is safe and no
   * submit navigation is in flight — but the very next {@code navigate(...)} still issues a fresh
   * GET on a connection WebKit has, under CI load, been seen to tear down mid-flight. WebKit
   * surfaces that aborted main-frame navigation as {@code HTTP/2 Error: INTERNAL_ERROR} ({@code
   * frameAbortedNavigation}), thrown as a {@link PlaywrightException} (concretely a {@code
   * DriverException}). The reset is transient — a brief settle plus a fresh GET succeeds — so
   * {@link #isTransientNavigationAbort} gates the retry to exactly those abort signatures and lets
   * every other failure (a real 4xx/5xx document, a timeout, a wrong URL) propagate immediately and
   * unmasked. The final attempt runs uncaught, so a persistent abort still fails the test with the
   * genuine Playwright error.
   *
   * @param page the page to navigate
   * @param url the absolute URL to load
   * @throws PlaywrightException if every attempt is aborted, or on the first non-transient
   *     navigation failure
   */
  static void navigate(Page page, String url) {
    // Attempts 1..N-1 retry a transient abort; the final attempt (below the loop) runs uncaught,
    // so a persistent failure propagates with its real error — mirrors login()/attemptLogin.
    for (int attempt = 1; attempt < NAVIGATE_MAX_ATTEMPTS; attempt++) {
      try {
        page.navigate(url);
        return;
      } catch (PlaywrightException abort) {
        if (!isTransientNavigationAbort(abort)) {
          throw abort;
        }
        System.out.printf(
            "[E2E][navigate] attempt %d/%d to %s aborted (%s); settling then retrying%n",
            attempt,
            NAVIGATE_MAX_ATTEMPTS,
            url,
            String.valueOf(abort.getMessage()).lines().findFirst().orElse("navigation aborted"));
      }
      // Let the reset HTTP/2 stream tear down and the page fall back to its prior, already-loaded
      // document before re-issuing the GET. The settle runs between attempts, outside the catch, so
      // it can never mask the abort it follows.
      page.waitForTimeout(NAVIGATE_RETRY_BACKOFF_MILLIS);
      page.waitForLoadState();
    }
    page.navigate(url);
  }

  /**
   * Reports whether a {@link PlaywrightException} from {@code page.navigate(...)} is a transient,
   * retryable abort of the navigation request itself — as opposed to a genuine failure of the
   * target page. It matches only the engine-specific abort signatures in {@link
   * #NAVIGATION_ABORT_SIGNATURES} (WebKit's {@code INTERNAL_ERROR} / {@code
   * frameAbortedNavigation}, Firefox's {@code NS_BINDING_ABORTED}, Chromium's {@code ERR_ABORTED},
   * and Playwright's "Navigation interrupted by another one"). A timeout, a 4xx/5xx, a DNS error or
   * any other navigation failure carries none of these, so it is reported non-transient and {@link
   * #navigate} lets it propagate unmasked.
   *
   * @param error the exception thrown by {@code page.navigate(...)}
   * @return {@code true} if the message carries a known transient-abort signature; {@code false}
   *     otherwise, including when the message is {@code null}
   */
  private static boolean isTransientNavigationAbort(PlaywrightException error) {
    String message = error.getMessage();
    if (message == null) {
      return false;
    }
    String lower = message.toLowerCase(Locale.ROOT);
    return NAVIGATION_ABORT_SIGNATURES.stream().anyMatch(lower::contains);
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
