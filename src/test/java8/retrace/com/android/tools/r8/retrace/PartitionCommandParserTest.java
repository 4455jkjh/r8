// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartitionCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public PartitionCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: partition [options] <proguard-map>",
            " where <proguard-map> is a generated mapping file and options are:",
            "",
            "  --output <partition-map>",
            "                          # Output destination of partitioned map.",
            "  --version               # Print the version.",
            "  --help",
            "  -h                      # Print this message."),
        PartitionCommandParser.getUsageMessage());
  }
}
