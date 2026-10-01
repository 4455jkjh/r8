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
public class LocalsAtThrowTestRunner extends DebugInfoTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void testLocalsAtThrow() throws Exception {
    Class clazz = LocalsAtThrowTest.class;

    AndroidApp d8App = compileWithD8(clazz);

    String expected = "3";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName(), parameters.getDexVm()));

    checkLocalsAtThrow(inspectMethod(d8App, clazz, "int", "localsAtThrow", "int"));
  }

  private void checkLocalsAtThrow(DebugInfoInspector info) {
    info.checkStartLine(9);
    info.checkLineHasExactLocals(9, "x", "int");
    info.checkLineHasExactLocals(10, "x", "int", "a", "int");
    info.checkLineHasExactLocals(11, "x", "int", "a", "int", "b", "int");
    info.checkNoLine(12);
    info.checkLineHasExactLocals(13, "x", "int", "a", "int", "b", "int");
    info.checkNoLine(14);
    info.checkLineHasExactLocals(15, "x", "int", "a", "int", "b", "int");
    info.checkNoLine(16);
    info.checkLineHasExactLocals(17, "x", "int", "a", "int", "b", "int");
  }
}
