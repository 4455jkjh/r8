// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk25.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.Reader;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ReaderBackportJava25Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK25)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ReaderBackportJava25Test(TestParameters parameters) {
    super(parameters, Reader.class, ReaderBackportJava25Main.class);
    registerTarget(AndroidApiLevel.B, 1);
  }

  public static class ReaderBackportJava25Main {

    public static void main(String[] args) throws Exception {
      int r = Reader.of("ba").read();
      assertEquals(r, 'b');
    }

    static void assertEquals(int x, int y) {
      if (x != y) {
        throw new RuntimeException("Not equals " + x + " and " + y);
      }
    }
  }
}
