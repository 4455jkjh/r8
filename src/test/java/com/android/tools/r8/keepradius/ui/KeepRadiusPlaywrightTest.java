// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.android.tools.r8.PlaywrightTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRadiusPlaywrightTest extends PlaywrightTestBase {

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

  static class Main {
    public static void main(String[] args) {
      System.out.println("Hello");
    }
  }
}
