// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InstanceFieldValueAnalysisWithCrossParameterWriteTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(B.class, H.class, Main.class)
        .addProgramClassFileData(getPreSuperFieldPutClassData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("DENY", "DENY");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(B.class, H.class, Main.class)
        .addProgramClassFileData(getPreSuperFieldPutClassData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("DENY", "DENY");
  }

  private static byte[] getPreSuperFieldPutClassData() throws IOException {
    return transformer(C.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKESPECIAL
                    && name.equals("<init>")
                    && getContext().getReference().getFormalTypes().isEmpty()) {
                  // Delay super() call until after the field put.
                  return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }

              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                if (opcode == Opcodes.PUTFIELD
                    && getContext().getReference().getFormalTypes().isEmpty()) {
                  super.visitMethodInsn(
                      Opcodes.INVOKESPECIAL, binaryName(B.class), "<init>", "()V", false);
                }
              }

              @Override
              public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(maxStack + 1, maxLocals);
              }
            })
        .transform();
  }

  @NoVerticalClassMerging
  public static class B {
    @NeverInline
    public B() {
      if (this instanceof C) {
        ((C) this).granted = false;
      }
    }
  }

  @NeverClassInline
  public static class C extends B {
    boolean granted;

    @NeverInline
    public C() {
      this.granted = true;
    }

    @NeverInline
    public C(boolean ignored) {
      this.granted = true;
      new H(this);
    }
  }

  @NeverClassInline
  public static class H {
    @NeverInline
    public H(C c) {
      c.granted = false;
    }
  }

  public static class Main {

    @NeverInline
    public static boolean checkPreSuperPut() {
      C c = new C();
      return c.granted;
    }

    @NeverInline
    public static boolean checkCrossParameterWrite() {
      C c = new C(true);
      return c.granted;
    }

    public static void main(String[] args) {
      System.out.println(checkPreSuperPut() ? "GRANT" : "DENY");
      System.out.println(checkCrossParameterWrite() ? "GRANT" : "DENY");
    }
  }
}
