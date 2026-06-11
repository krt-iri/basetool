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
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ad-hoc visual verification harness for the blueprints master-detail page against an ALREADY
 * RUNNING local test stack ({@code E2E_BASE_URL} env; self-skipping without {@code BP_CHECK=true}).
 * Logs in as the synthetic test admin, walks the empty state, stages and adds a blueprint through
 * the typeahead, then screenshots the master-detail view and dumps console errors. Not part of CI.
 */
@Tag("e2e")
class BlueprintsMockupCheckE2eTest {

  private static Playwright playwright;
  private static Browser browser;

  /** Boots a headless Chromium for the walk-through. */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  /** Releases the browser and driver process. */
  @AfterAll
  static void tearDown() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /** Walks empty state, add flow, and master-detail rendering; captures screenshots. */
  @Test
  void walkBlueprintsPage() {
    if (!"true".equals(System.getenv("BP_CHECK"))) {
      System.out.println("[bp-check] BP_CHECK env missing - skipping");
      return;
    }
    String baseUrl = System.getenv().getOrDefault("E2E_BASE_URL", "https://localhost:18081");
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
      Page page = context.newPage();
      StringBuilder consoleLog = new StringBuilder();
      page.onConsoleMessage(
          msg -> {
            if ("error".equals(msg.type()) || "warning".equals(msg.type())) {
              consoleLog
                  .append('[')
                  .append(msg.type())
                  .append("] ")
                  .append(msg.text())
                  .append('\n');
            }
          });
      page.onPageError(err -> consoleLog.append("[pageerror] ").append(err).append('\n'));

      E2eSupport.login(page, baseUrl, "test-admin", "test-admin-pw");

      page.navigate(baseUrl + "/personal-inventory/blueprints");
      page.waitForLoadState();
      page.waitForTimeout(600);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setFullPage(true)
              .setPath(Paths.get("build", "e2e", "bp-initial.png")));

      // Add a blueprint through the typeahead if the collection is still empty.
      Locator rows = page.locator("#krt-bp-master-rows .master-row");
      if (rows.count() == 0) {
        page.locator("#krt-bp-search-input").fill("Demo");
        page.waitForTimeout(900);
        Locator hit = page.locator("#krt-bp-search-results .krt-bp-result:not([disabled])");
        System.out.println("[bp-check] typeahead hits: " + hit.count());
        if (hit.count() > 0) {
          hit.first().click();
          page.waitForTimeout(300);
          page.screenshot(
              new Page.ScreenshotOptions()
                  .setFullPage(true)
                  .setPath(Paths.get("build", "e2e", "bp-staged.png")));
          page.locator("#krt-bp-add-selected").click();
          page.waitForTimeout(2000);
          page.navigate(baseUrl + "/personal-inventory/blueprints");
          page.waitForLoadState();
        }
      }

      page.waitForTimeout(800);
      Object probe =
          page.evaluate(
              "() => ({ rows: document.querySelectorAll('#krt-bp-master-rows .master-row').length,"
                  + " active: document.querySelectorAll('.master-row.is-active').length,"
                  + " detailVisible: !!document.querySelector('#krt-bp-detail-content') &&"
                  + " !document.querySelector('#krt-bp-detail-content').hidden,"
                  + " blocks: document.querySelectorAll('.quality-block').length,"
                  + " bpParam: new URLSearchParams(location.search).get('bp') })");
      System.out.println("[bp-check] probe: " + probe);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setFullPage(true)
              .setPath(Paths.get("build", "e2e", "bp-master-detail.png")));

      System.out.println("[bp-check] console messages:\n" + consoleLog);
    }
  }
}
