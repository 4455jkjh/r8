// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexSegmentsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexSegmentsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: dexsegments [options] <input-files>",
            " where <input-files> are dex files",
            "  --version               # Print the version of r8.",
            "  --help",
            "  -h                      # Print this message.",
            "  --csv                   # Print segments in csv format."),
        DexSegments.Command.usageMessage());
  }

  @Test
  public void testVersion() throws Exception {
    DexSegments.Command command = DexSegments.Command.parse(new String[] {"--version"}).build();
    assertTrue(command.isPrintVersion());
    assertNull(DexSegments.run(command));
  }
}
