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
public class DisassembleTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DisassembleTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: disasm [options] <input-files>",
            " where <input-files> are dex files",
            " and options are:",
            "  --all                   # Include all information in disassembly.",
            "  --smali                 # Disassemble using smali syntax.",
            "  --ir                    # Print IR before and after optimization.",
            "  --nocode                # No printing of code objects.",
            "  --pg-map <file>         # Proguard map <file> for mapping names.",
            "  --output <file/dir>     # Specify a file or directory to write to.",
            "  --class <descriptor>    # Only disassemble the given class (e.g.,"
                + " Lcom/example/Class;).",
            "  --field <descriptor>    # Only disassemble the given field (e.g.,"
                + " Lcom/example/Class;->field:I).",
            "  --method <descriptor>   # Only disassemble the given method (e.g.,"
                + " Lcom/example/Class;->method()V).",
            "  --version               # Print the version of r8.",
            "  --help                  # Print this message."),
        Disassemble.DisassembleCommand.usageMessage());
  }
}
