// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.Disassemble;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Invoke R8 on the dex files extracted from GMSCore.apk to disassemble the dex code.
@RunWith(Parameterized.class)
public class GMSCoreV10DisassemblerTest extends GMSCoreCompilationTestBase {

  @Parameters(name = "{0}, deobfuscate: {1} smali: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private final boolean deobfuscate;
  private final boolean smali;

  public GMSCoreV10DisassemblerTest(TestParameters parameters, boolean deobfuscate, boolean smali) {
    parameters.assertNoneRuntime();
    this.deobfuscate = deobfuscate;
    this.smali = smali;
  }

  @Test
  public void testDisassemble() throws Exception {
    Path appDir = TestDeps.getGmscoreV10Dir();
    // This test only ensures that we do not break disassembling of dex code. It does not
    // check the generated code. To make it fast, we get rid of the output.
    PrintStream originalOut = System.out;
    System.setOut(
        new PrintStream(
            new OutputStream() {
              @Override
              public void write(int b) {
                // Intentionally empty.
              }
            }));

    try {
      Disassemble.DisassembleCommand.Builder builder = Disassemble.DisassembleCommand.builder();
      builder.setUseSmali(smali);
      if (deobfuscate) {
        builder.setProguardMapFile(appDir.resolve(PG_MAP));
      }
      builder.addProgramFiles(appDir.resolve(RELEASE_APK_X86));
      Disassemble.DisassembleCommand command = builder.build();
      Disassemble.disassemble(command);
    } finally {
      // Restore System.out for good measure.
      System.setOut(originalOut);
    }
  }
}
