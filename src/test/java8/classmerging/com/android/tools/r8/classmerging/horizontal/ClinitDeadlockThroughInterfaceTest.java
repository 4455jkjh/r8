// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClinitDeadlockThroughInterfaceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        // TODO(b/557268346): Should not merge B and C.
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertIsCompleteMergeGroup(B.class, C.class))
        .compile();
  }

  static class Main {

    // @Keep
    public static void thread0() {
      // This will take the lock for A, then I, and then wait to take the lock for B (BC if B and C
      // are merged).
      System.out.println(A.b);
    }

    // @Keep
    public static void thread1() {
      // This will take the lock for C (BC if B and C are merged) and then wait to take the lock for
      // its superclass A.
      System.out.println(new C());
    }
  }

  interface I {

    B b = new B();
  }

  static class A implements I {

    static B b = I.b;
  }

  static class B extends A {}

  static class C extends A {}
}
