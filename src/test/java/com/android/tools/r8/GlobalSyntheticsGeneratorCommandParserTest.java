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
public class GlobalSyntheticsGeneratorCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GlobalSyntheticsGeneratorCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: globalsyntheticsgenerator [options] where options are:",
            "",
            "  --min-api <number>      # Minimum Android API level compatibility (default: 1).",
            "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
            "  --output <globals-file> # Output result in <globals-file>.",
            "  --classfile             # Generate globals for only classfile to classfile"
                + " desugaring.",
            "                          # (By default globals for both classfile and dex desugaring"
                + " are generated).",
            "  --verbose-synthetic-names",
            "                          # Enable verbose synthetic names that use the"
                + " `$$ExternalSynthetic` marker.",
            "  --version               # Print the version of globalsyntheticsgenerator.",
            "  --help                  # Print this message."),
        GlobalSyntheticsGeneratorCommandParser.getUsageMessage());
  }
}
