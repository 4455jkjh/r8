// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

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
public class DesugaredMethodsListCommandTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DesugaredMethodsListCommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: desugaredmethods [options] where  options are:",
            "  --output <file>         # Output result in <file>.",
            "                          # <file> must be an existing directory or a zip file.",
            "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
            "  --min-api <number>      # Minimum Android API level compatibility (default: 1).",
            "  --version               # Print the version of DesugaredMethods.",
            "  --help                  # Print this message.",
            "  --desugared-lib <file>  # Specify desugared library configuration.",
            "                          # <file> is a desugared library configuration (json).",
            "  --android-platform-build",
            "                          # Compile as a platform build where the"
                + " runtime/bootclasspath",
            "                          # is assumed to be the version specified by --min-api.",
            "  --desugared-lib-jar <file>",
            "                          # Specify desugared library jar."),
        DesugaredMethodsListCommand.getUsageMessage());
  }
}
