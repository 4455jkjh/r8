// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius.ui;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.PlaywrightTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepradius.KeepRadiusHtmlReportGenerator;
import com.android.tools.r8.keepradius.proto.KeepRadiusContainer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRadiusTitlePlaywrightTest extends PlaywrightTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testTitle() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .enableConfigurationAnalysisData()
        .enableConfigurationAnalysisReport()
        .compile()
        .apply(
            compileResult -> {
              KeepRadiusContainer proto =
                  KeepRadiusContainer.parseFrom(compileResult.getConfigurationAnalysisData());
              assertEquals("R8 Configuration Analyzer", proto.getTitle());
            })
        .inspectKeepRadiusHtmlReport(
            this::getPage, inspector -> inspector.assertTitle("R8 Configuration Analyzer"))
        .apply(
            compileResult -> {
              KeepRadiusContainer proto =
                  KeepRadiusContainer.parseFrom(compileResult.getConfigurationAnalysisData())
                      .toBuilder()
                      .setTitle("Hello, world!")
                      .build();
              compileResult.inspectKeepRadiusHtmlReport(
                  this::getPage,
                  KeepRadiusHtmlReportGenerator.generateHtmlReport(proto),
                  inspector -> inspector.assertTitle("Hello, world!"));
            });
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
