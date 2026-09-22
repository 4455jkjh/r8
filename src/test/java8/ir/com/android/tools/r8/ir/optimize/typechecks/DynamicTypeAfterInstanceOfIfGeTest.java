// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.typechecks;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DynamicTypeAfterInstanceOfIfGeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              // Since the instanceof is guarded by IFGE (which does not discriminate on boolean),
              // R8 must not assume that `a` has type `B`, and therefore the checkcast must be kept.
              assertTrue(
                  mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isCheckCast));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(ClassCastException.class);
  }

  private byte[] getTransformedMain() throws Exception {
    return transformer(Main.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitJumpInsn(int opcode, Label label) {
                if (opcode == Opcodes.IFEQ) {
                  super.visitJumpInsn(Opcodes.IFLT, label);
                } else {
                  super.visitJumpInsn(opcode, label);
                }
              }
            })
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      A a = args.length == 0 ? new A() : new B();
      // After transformation: if ((a instanceof B) >= 0)
      if (a instanceof B) {
        ((B) a).m();
      }
    }
  }

  @NoVerticalClassMerging
  static class A {}

  static class B extends A {

    public void m() {}
  }
}
