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
public class ExtractR8RulesCommandTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ExtractR8RulesCommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: TBD",
            "  --rules-output <file>      # Output the extracted keep rules.",
            "  --compiler-version <version>  # Output the proguard rules extracted.",
            "  --include-origin-comments  # Include comments with origin for extracted rules.",
            "  --version                  # Print the version.",
            "  --help                     # Print this message."),
        ExtractR8RulesCommand.USAGE_MESSAGE);
  }
}
