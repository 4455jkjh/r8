// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

// This is a regression test for b/558351430.
@RunWith(Parameterized.class)
public class MemberRebindingInvokeSpecialToIndirectSuperInterfaceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("I::m");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(I.class, J.class, A.class, TestClass.class)
        .addProgramClassFileData(getTransformedK())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, A.class, TestClass.class)
        .addProgramClassFileData(getTransformedK())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, A.class, TestClass.class)
        .addProgramClassFileData(getTransformedK())
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(I.class, J.class, K.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .applyIf(
            parameters.isCfRuntime(),
            compileResult ->
                compileResult.inspect(
                    inspector -> {
                      MethodSubject callMethod =
                          inspector.clazz(K.class).uniqueMethodWithOriginalName("call");
                      assertThat(callMethod, isPresent());
                      assertTrue(
                          callMethod
                              .streamInstructions()
                              .anyMatch(
                                  instruction ->
                                      instruction.isCfInstruction()
                                          && instruction.asCfInstruction().isInvokeSpecial()
                                          && instruction
                                              .getMethod()
                                              .getHolderType()
                                              .isIdenticalTo(
                                                  inspector
                                                      .clazz(I.class)
                                                      .asFoundClassSubject()
                                                      .getDexProgramClass()
                                                      .getType())));
                    }))
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/558351430): Member rebinding rebinds the invokespecial from K.m to I.m on CF,
        //  which fails classfile verification because I is not a direct superinterface of K.
        .applyIf(
            parameters.isCfRuntime(),
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class),
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  // Transform the invokeinterface in K to invokespecial. See b/558351430#comment2.
  private byte[] getTransformedK() throws Exception {
    return transformer(K.class)
        .transformMethodInsnInMethod(
            "call",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("m")) {
                visitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, binaryName(K.class), "m", "()V", true);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public interface I {
    default void m() {
      System.out.println("I::m");
    }
  }

  @NoVerticalClassMerging
  public interface J extends I {}

  public interface K extends J {
    @NeverInline
    static void call(K k) {
      k.m();
    }
  }

  static class A implements K {}

  static class TestClass {
    public static void main(String[] args) {
      K.call(new A());
    }
  }
}
