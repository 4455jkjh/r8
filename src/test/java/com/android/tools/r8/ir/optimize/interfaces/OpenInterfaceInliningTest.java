// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.interfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class OpenInterfaceInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines(false));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines(false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .addKeepClassAndMembersRules(Main.class)
        // TODO(b/214496607): I should not be merged into A in the first place, since I is open.
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutputLines(true));
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(I.class, A.class, B.class);
  }

  private byte[] getTransformedMainClass() throws IOException {
    return transformer(Main.class)
        .transformTypeInsnInMethod(
            "getB",
            (opcode, type, visitor) -> {
              assertEquals(opcode, Opcodes.NEW);
              assertEquals(type, binaryName(A.class));
              visitor.visitTypeInsn(opcode, binaryName(B.class));
            })
        .transformMethodInsnInMethod(
            "getB",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals(opcode, Opcodes.INVOKESPECIAL);
              assertEquals(owner, binaryName(A.class));
              assertEquals(name, "<init>");
              visitor.visitMethodInsn(opcode, binaryName(B.class), name, descriptor, isInterface);
            })
        .transform();
  }

  private List<String> getExpectedOutputLines(boolean isR8) {
    if (isR8) {
      // TODO(b/214496607): R8 should not inline the invoke instruction since I is open.
      return ImmutableList.of("A", "A");
    }
    return ImmutableList.of("A", "ICCE");
  }

  static class Main {

    public static void main(String[] args) {
      getA().m();
      try {
        getB().m();
      } catch (IncompatibleClassChangeError e) {
        System.out.println("ICCE");
      }
    }

    static I getA() {
      return new A();
    }

    static I getB() {
      return new A(); // transformed into new B().
    }
  }

  @NoVerticalClassMerging
  interface I {

    void m();
  }

  static class A implements I {

    @Override
    public void m() {
      System.out.println("A");
    }
  }

  static class B {

    public void m() {
      System.out.println("B");
    }
  }
}
