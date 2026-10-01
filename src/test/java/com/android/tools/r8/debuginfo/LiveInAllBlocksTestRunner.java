// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LiveInAllBlocksTestRunner extends DebugInfoTestBase {

  // Regression test for b/65272487.
  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void testLiveInAllBlocks() throws Exception {
    Class clazz = LiveInAllBlocksTest.class;
    AndroidApp d8App = compileWithD8(clazz);

    String expected = "42";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName(), parameters.getDexVm()));

    checkFoo(inspectMethod(d8App, clazz, "int", "foo", "int"));
  }

  private void checkFoo(DebugInfoInspector info) {
    info.checkStartLine(9);
    for (int line : new int[] {14, 18, 23, 24, 25, 27, 28, 30, 31, 34, 35, 37, 38, 40, 41}) {
      info.checkLineHasAtLeastLocals(line, "x", "int", "y", "int");
    }
  }
}
