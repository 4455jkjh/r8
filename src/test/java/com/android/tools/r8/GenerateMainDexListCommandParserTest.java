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
public class GenerateMainDexListCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateMainDexListCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.joinLines(
            "Usage: maindex [options] <input-files>",
            " where <input-files> are JAR files",
            " and options are:",
            "  --lib <file>             # Add <file> as a library resource.",
            "  --main-dex-rules <file>  # Proguard keep rules for classes to place in the",
            "                           # primary dex file.",
            "  --main-dex-list <file>   # List of classes to place in the primary dex file.",
            "  --main-dex-list-output <file>  # Output the full main-dex list in <file>.",
            "  --version                # Print the version.",
            "  --help                   # Print this message."),
        GenerateMainDexListCommand.USAGE_MESSAGE);
  }
}
