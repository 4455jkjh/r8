// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LazyFieldRedundantLoadStoreEliminationTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "B", "C", "D");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "B", "C", "D");
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    MethodSubject getOrCreateMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("getOrCreate");
    assertEquals(
        1, getOrCreateMethod.streamInstructions().filter(InstructionSubject::isStaticGet).count());

    MethodSubject getOrCreateSwitchMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("getOrCreateSwitch");
    assertEquals(
        // Due to incremental compilations D8 should not rely on the class hierarchy, and thus D8
        // cannot compute the type of the phi when the phi operands have different types.
        isR8 ? 1 : 2,
        getOrCreateSwitchMethod
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
  }

  static class Main {

    static A sInstance;

    public static void main(String[] args) {
      System.out.println(getOrCreate());
      sInstance = null;
      System.out.println(getOrCreateSwitch(0));
      sInstance = null;
      System.out.println(getOrCreateSwitch(1));
      sInstance = null;
      System.out.println(getOrCreateSwitch(2));
      sInstance = null;
      System.out.println(getOrCreateSwitch(42));
    }

    public static Object getOrCreate() {
      if (sInstance == null) {
        sInstance = new A();
      }
      return sInstance;
    }

    public static Object getOrCreateSwitch(int i) {
      if (sInstance == null) {
        switch (i) {
          case 0:
            sInstance = new A();
            break;
          case 1:
            sInstance = new B();
            break;
          case 2:
            sInstance = new C();
            break;
          default:
            sInstance = new D();
            break;
        }
      }
      return sInstance;
    }
  }

  @NoHorizontalClassMerging
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  @NoHorizontalClassMerging
  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  @NoHorizontalClassMerging
  static class C extends A {

    @Override
    public String toString() {
      return "C";
    }
  }

  @NoHorizontalClassMerging
  static class D extends A {

    @Override
    public String toString() {
      return "D";
    }
  }
}
