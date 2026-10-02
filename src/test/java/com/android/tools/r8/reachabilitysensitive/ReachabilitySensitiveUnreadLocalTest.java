// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.reachabilitysensitive;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import dalvik.annotation.optimization.ReachabilitySensitive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReachabilitySensitiveUnreadLocalTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClasses(Main.class, ReachabilitySensitive.class)
        .release()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("java.lang.Object");
  }

  private void inspect(CodeInspector inspector) {
    // The copy to the unread local `alias` must be kept: once `obj` is overwritten, it is the only
    // reference keeping the first object reachable until the end of the scope of `alias`.
    MethodSubject method = inspector.clazz(Main.class).uniqueMethodWithOriginalName("m");
    assertTrue(method.streamInstructions().anyMatch(InstructionSubject::isMove));
  }

  static class Main {

    @ReachabilitySensitive long handle = 42;

    static Object create() {
      return new Object();
    }

    void m() {
      Object obj = new Object();
      Object alias = obj;
      obj = create();
      System.out.println(obj.getClass().getName());
    }

    public static void main(String[] args) {
      new Main().m();
    }
  }
}
