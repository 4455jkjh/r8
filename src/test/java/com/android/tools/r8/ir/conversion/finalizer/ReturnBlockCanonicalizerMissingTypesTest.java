// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression test for merging return blocks whose return values are based on missing classes. The
 * join of such values is java.lang.Object, which does not type check against the return type.
 */
@RunWith(Parameterized.class)
public class ReturnBlockCanonicalizerMissingTypesTest extends TestBase {

  private static final Class<?>[] MISSING_CLASSES =
      new Class<?>[] {Operation.class, A.class, B.class, Base.class, C.class, D.class};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    Path missingClasses =
        testForD8(parameters.getBackend())
            .addProgramClasses(MISSING_CLASSES)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForR8(parameters)
        .addProgramClasses(Main.class, Host.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Host.class)
        .addDontWarn(MISSING_CLASSES)
        .compile()
        .addRunClasspathFiles(missingClasses)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "C", "D");
  }

  interface Operation {}

  static class A implements Operation {

    static A create() {
      return new A();
    }

    @Override
    public String toString() {
      return "A";
    }
  }

  static class B implements Operation {

    static B create() {
      return new B();
    }

    @Override
    public String toString() {
      return "B";
    }
  }

  static class Base {}

  static class C extends Base {

    static C create() {
      return new C();
    }

    @Override
    public String toString() {
      return "C";
    }
  }

  static class D extends Base {

    static D create() {
      return new D();
    }

    @Override
    public String toString() {
      return "D";
    }
  }

  static class Host {

    static Operation parseInterface(int x) {
      if (x == 0) {
        return A.create();
      }
      return B.create();
    }

    static Base alwaysThrows(int x) {
      if (x == 0) {
        throw new IllegalStateException();
      }
      throw new IllegalArgumentException();
    }

    static Base parseClass(int x) {
      if (x == 0) {
        return C.create();
      }
      return D.create();
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(Host.parseInterface(System.nanoTime() > 0 ? 0 : 1));
      System.out.println(Host.parseInterface(System.nanoTime() > 0 ? 1 : 0));
      System.out.println(Host.parseClass(System.nanoTime() > 0 ? 0 : 1));
      System.out.println(Host.parseClass(System.nanoTime() > 0 ? 1 : 0));
    }
  }
}
