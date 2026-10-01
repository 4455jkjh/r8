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
public class ConditionalLocalTestRunner extends DebugInfoTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void testConditionalLocal() throws Exception {
    Class clazz = ConditionalLocalTest.class;

    AndroidApp d8App = compileWithD8(clazz);

    String expected = "42";
    assertEquals(expected, runOnJava(clazz));
    assertEquals(expected, runOnArt(d8App, clazz.getCanonicalName(), parameters.getDexVm()));

    checkConditonalLocal(inspectMethod(d8App, clazz, "void", "foo", "int"));
  }

  private void checkConditonalLocal(DebugInfoInspector info) {
    String self = ConditionalLocalTest.class.getCanonicalName();
    String Integer = "java.lang.Integer";
    info.checkStartLine(9);
    info.checkLineHasExactLocals(9, "this", self, "x", "int");
    info.checkLineHasExactLocals(10, "this", self, "x", "int");
    info.checkLineHasExactLocals(11, "this", self, "x", "int", "obj", Integer);
    info.checkLineHasExactLocals(12, "this", self, "x", "int", "obj", Integer, "l", "long");
    info.checkLineHasExactLocals(13, "this", self, "x", "int", "obj", Integer, "l", "long");
    info.checkNoLine(14);
    info.checkLineHasExactLocals(15, "this", self, "x", "int");
  }
}
