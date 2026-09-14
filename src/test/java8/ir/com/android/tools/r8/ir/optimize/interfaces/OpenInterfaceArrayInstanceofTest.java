// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.interfaces;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class OpenInterfaceArrayInstanceofTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true", "false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getTransformedMainClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557271665): Should succeed with expected output.
        .assertSuccessWithOutputLines("true", "true");
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(I.class, A.class, B.class);
  }

  private byte[] getTransformedMainClass() {
    return transformer(Main.class)
        .transformTypeInsnInMethod(
            "runB",
            (opcode, type, visitor) -> {
              if (opcode == Opcodes.NEW) {
                assertEquals(type, binaryName(A.class));
                visitor.visitTypeInsn(opcode, binaryName(B.class));
              } else if (opcode == Opcodes.ANEWARRAY) {
                assertEquals(type, binaryName(A.class));
                visitor.visitTypeInsn(opcode, binaryName(B.class));
              } else {
                visitor.visitTypeInsn(opcode, type);
              }
            })
        .transformMethodInsnInMethod(
            "runB",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (opcode == Opcodes.INVOKESPECIAL && owner.equals(binaryName(A.class))) {
                visitor.visitMethodInsn(opcode, binaryName(B.class), name, descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      runA();
      runB();
    }

    static void runA() {
      test(new A[] {new A()});
    }

    static void runB() {
      test(new A[] {new A()}); // transformed to test(new B[] {new B()})
    }

    @NeverInline
    static void test(I[] arr) {
      I i = arr[0];
      if (i != null) {
        System.out.println(i instanceof I);
      } else {
        System.out.println(true);
      }
    }
  }

  @NoVerticalClassMerging
  interface I {}

  static class A implements I {}

  static class B {}
}
