// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.PlaywrightTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.microsoft.playwright.Locator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationConfigurationAnalysisOutputTest extends PlaywrightTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testDataConsumer() throws Exception {
    testForR8Partial(Backend.DEX)
        .addInnerClasses(getClass())
        .enableConfigurationAnalysisData()
        .setMinApi(AndroidApiLevel.N)
        .setR8PartialConfiguration(b -> b.excludeClasses(Main.class).includeClasses(A.class))
        .compile()
        .inspectKeepRadiusData(
            data -> {
              assertNotNull(data);
              assertNotEquals(0, data.length);
            });
  }

  @Test
  public void testReportConsumer() throws Exception {
    testForR8Partial(Backend.DEX)
        .addInnerClasses(getClass())
        .enableConfigurationAnalysisReport()
        .setMinApi(AndroidApiLevel.N)
        .setR8PartialConfiguration(b -> b.excludeClasses(Main.class).includeClasses(A.class))
        .compile()
        .inspectKeepRadiusHtmlReport(
            this::getPage,
            inspector -> {
              Locator shrinkingScoreCard = page.locator("#card-total-shrinking");
              assertThat(shrinkingScoreCard).hasText("0.0%");
            });
  }

  public static class Main {

    public static void main(String[] args) {
      A.m();
    }
  }

  static class A {

    static void m() {}
  }
}
