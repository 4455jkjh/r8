// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.origin.Origin;
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
            "  --min-api <number>      # Minimum Android API level compatibility (default: 1).",
            "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
            "  --output <globals-file> # Output result in <globals-file>.",
            "  --classfile             # Generate globals for only classfile to classfile"
                + " desugaring. (By default",
            "                          # globals for both classfile and dex desugaring are"
                + " generated).",
            "  --verbose-synthetic-names",
            "                          # Enable verbose synthetic names that use the"
                + " `$$ExternalSynthetic` marker.",
            "  --version               # Print the version of globalsyntheticsgenerator.",
            "  --help                  # Print this message."),
        GlobalSyntheticsGeneratorCommandParser.getUsageMessage());
  }

  @Test
  public void testParseDefault() {
    GlobalSyntheticsGeneratorCommand command =
        GlobalSyntheticsGeneratorCommand.parse(new String[0], Origin.unknown()).build();
    assertNotNull(command.getInternalOptions().getGlobalSyntheticsConsumer());
    assertFalse(command.isPrintHelp());
    assertFalse(command.isPrintVersion());
  }

  @Test
  public void testParseOutput() {
    GlobalSyntheticsGeneratorCommand command =
        GlobalSyntheticsGeneratorCommand.parse(
                new String[] {"--output", "out.jar"}, Origin.unknown())
            .build();
    assertNotNull(command.getInternalOptions().getGlobalSyntheticsConsumer());
  }
}
