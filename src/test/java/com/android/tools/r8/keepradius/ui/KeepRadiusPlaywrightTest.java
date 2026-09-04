// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.android.tools.r8.PlaywrightTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRadiusPlaywrightTest extends PlaywrightTestBase {

  private static final String GREEN_TEXT = "rgb(22, 101, 52)";
  private static final String RED_TEXT = "rgb(185, 28, 28)";
  private static final String YELLOW_TEXT = "rgb(176, 96, 0)";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testHtmlReportRendering() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              inspector
                  .assertTitle("R8 Configuration Analyzer")
                  .assertTotalObfuscationNotEmpty()
                  // 2. Assert table contains the keep rule
                  .assertTableContains("-keep class ** { *; }")
                  // 3. Search for "keep"
                  .search("keep")
                  .assertTableContains("-keep class ** { *; }")
                  // Search for "nonexistent"
                  .search("nonexistent")
                  .assertTableContains("No results found.")
                  // Clear search
                  .search("")
                  // 4. Click the "Unused" tab (should be empty for our keep rule)
                  .clickLensTab("Unused")
                  .assertTableContains("No results found.")
                  // Go back to "All"
                  .clickLensTab("All")
                  // 5. Click the rule row to drill down to details
                  .clickRowWithText("-keep class ** { *; }")
                  // Assert details view is visible
                  .assertVisible("#details-view")
                  .assertHidden("#report-view")
                  // Assert Main class is kept in details
                  .assertDetailsClassesContains(Main.class.getTypeName())
                  // Go back
                  .clickDetailsBackToSummary()
                  .assertVisible("#report-view")
                  .assertHidden("#details-view");
            });
  }

  @Test
  public void testHtmlReportStylesAndConsoleErrors() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Assert computed styles for key elements
              // Body styles
              assertThat(page.locator("body")).hasCSS("background-color", "rgb(248, 250, 252)");
              assertThat(page.locator("body")).hasCSS("display", "flex");

              // Header styles
              assertThat(page.locator("header")).hasCSS("background-color", "rgb(255, 255, 255)");
              assertThat(page.locator("header"))
                  .hasCSS("border-bottom-color", "rgb(226, 232, 240)");
            });
  }

  @Test
  public void testHtmlReportScore80Green() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Header styles.
              assertElementHasTextAndColor(page, "#total-shrinking", "80.0%", GREEN_TEXT);
              assertElementHasTextAndColor(page, "#total-optimization", "80.0%", GREEN_TEXT);
              assertElementHasTextAndColor(page, "#total-obfuscation", "80.0%", GREEN_TEXT);

              // Cards styles.
              assertElementHasTextAndColor(page, "#card-total-shrinking", "80.0%", GREEN_TEXT);
              assertElementHasTextAndColor(page, "#card-total-optimization", "80.0%", GREEN_TEXT);
              assertElementHasTextAndColor(page, "#card-total-obfuscation", "80.0%", GREEN_TEXT);
            });
  }

  @Test
  public void testHtmlReportScore70Yellow() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepclassmembers class * { void a(); }")
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Header styles.
              assertElementHasTextAndColor(page, "#total-shrinking", "70.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#total-optimization", "70.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#total-obfuscation", "70.0%", YELLOW_TEXT);

              // Cards styles.
              assertElementHasTextAndColor(page, "#card-total-shrinking", "70.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#card-total-optimization", "70.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#card-total-obfuscation", "70.0%", YELLOW_TEXT);
            });
  }

  @Test
  public void testHtmlReportScore60Yellow() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepclassmembers class * { void a(); void b(); }")
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Header styles.
              assertElementHasTextAndColor(page, "#total-shrinking", "60.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#total-optimization", "60.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#total-obfuscation", "60.0%", YELLOW_TEXT);

              // Cards styles.
              assertElementHasTextAndColor(page, "#card-total-shrinking", "60.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#card-total-optimization", "60.0%", YELLOW_TEXT);
              assertElementHasTextAndColor(page, "#card-total-obfuscation", "60.0%", YELLOW_TEXT);
            });
  }

  @Test
  public void testHtmlReportScore50Red() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepclassmembers class * { void a(); void b(); c(); }")
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Header styles.
              assertElementHasTextAndColor(page, "#total-shrinking", "50.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#total-optimization", "50.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#total-obfuscation", "50.0%", RED_TEXT);

              // Cards styles.
              assertElementHasTextAndColor(page, "#card-total-shrinking", "50.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#card-total-optimization", "50.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#card-total-obfuscation", "50.0%", RED_TEXT);
            });
  }

  @Test
  public void testHtmlReportScore0Red() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepClassAndMembersRules(Main.class)
        .enableConfigurationAnalysisReport()
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              // Header styles.
              assertElementHasTextAndColor(page, "#total-shrinking", "0.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#total-optimization", "0.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#total-obfuscation", "0.0%", RED_TEXT);

              // Cards styles.
              assertElementHasTextAndColor(page, "#card-total-shrinking", "0.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#card-total-optimization", "0.0%", RED_TEXT);
              assertElementHasTextAndColor(page, "#card-total-obfuscation", "0.0%", RED_TEXT);
            });
  }

  private static void assertElementHasTextAndColor(
      Page page, String selector, String text, String color) {
    Locator locator = page.locator(selector);
    assertThat(locator).hasText(text);
    assertThat(locator).hasCSS("color", color);
  }

  static class Main {

    public static void main(String[] args) {
      a();
    }

    static void a() {
      b();
    }

    static void b() {
      c();
    }

    static void c() {
      d();
    }

    static void d() {
      e();
    }

    static void e() {
      f();
    }

    static void f() {
      g();
    }

    static void g() {
      h();
    }

    static void h() {
      System.out.println("Hello");
    }
  }
}
