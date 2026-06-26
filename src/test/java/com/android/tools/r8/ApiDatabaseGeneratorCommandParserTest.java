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
public class ApiDatabaseGeneratorCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiDatabaseGeneratorCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: apidatabasegenerator [options] <input-files>",
            "where <input-files> are Android API XML files (e.g., api-versions.xml) to merge,",
            "and options are:",
            "  --help",
            "  -h                      # Print help.",
            "  --version               # Print version.",
            "  --output <database-file>",
            "                          # Output result in <database-file> (must be a file, not a"
                + " directory).",
            "                          # Defaults to 'api_database.ser'.",
            "  --map-diagnostics[:<type>] <from-level> <to-level>",
            "                          # Map diagnostics of <type> (default any) reported as"
                + " <from-level> to",
            "                          # <to-level> where <from-level> and <to-level> are one of"
                + " 'none', 'info',",
            "                          # 'warning', or 'error', and the optional <type> is either"
                + " the simple or",
            "                          # fully qualified Java type name of a diagnostic. If <type>"
                + " is unspecified,",
            "                          # all diagnostics at <from-level> will be mapped. Note that"
                + " fatal compiler",
            "                          # errors cannot be mapped."),
        ApiDatabaseGeneratorCommandParser.getUsageMessage());
  }
}
