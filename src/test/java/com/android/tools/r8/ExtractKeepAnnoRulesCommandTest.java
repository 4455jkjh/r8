// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExtractKeepAnnoRulesCommandTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ExtractKeepAnnoRulesCommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: EXPERIMENTAL tool to extract keep rules from keep annotations",
            "  --rules-output <file>      # Output the extracted keep rules.",
            "  --rules-target r8|pg       # Optimizer rules are for (default r8).",
            "  --version                  # Print the version.",
            "  --help                     # Print this message."),
        ExtractKeepAnnoRulesCommand.USAGE_MESSAGE);
  }
}
