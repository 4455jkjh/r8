// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress65007724 extends JasminTestBase {

  private final TestParameters parameters;

  public Regress65007724(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testThat16BitsIndexAreAllowed() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    for (int i = 0; i < 35000; i++) {
      builder.addClass("C" + i);
    }

    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticField("f", "LC34000;", null);

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Hello World!\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");

    SingleTestRunResult<?> d8TestRunResult =
        testForD8(parameters)
            .addProgramClassFileData(builder.buildClasses())
            .compile()
            .writeToZip(
                p -> {
                  try {
                    assertTrue(ZipUtils.containsEntry(p, "classes.dex"));
                    assertEquals(
                        parameters.canUseNativeMultidex()
                            && parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
                        ZipUtils.containsEntry(p, "classes2.dex"));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .run(parameters.getRuntime(), clazz.name);
    d8TestRunResult.assertSuccessWithOutput("Hello World!");
  }
}
