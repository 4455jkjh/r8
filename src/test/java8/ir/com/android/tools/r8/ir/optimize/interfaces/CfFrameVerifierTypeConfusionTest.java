// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.interfaces;

import com.android.tools.r8.NoHorizontalClassMerging;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class CfFrameVerifierTypeConfusionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ICCE");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ICCE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers class " + Main.class.getTypeName() + " { public static *; }",
            "-neverinline class "
                + AuthCheckFactory.class.getTypeName()
                + " { static *** make(...); }")
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ICCE");
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(AuthCheck.class, RealAuth.class, Fake.class, Main.class);
  }

  private byte[] getProgramClassFileData() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        binaryName(AuthCheckFactory.class),
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "make",
            "()L" + binaryName(AuthCheck.class) + ";",
            null,
            null);
    mv.visitCode();
    Label lStart = new Label();
    Label lDead = new Label();
    Label lTarget = new Label();

    mv.visitLabel(lStart);
    mv.visitTypeInsn(Opcodes.NEW, binaryName(Fake.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, binaryName(Fake.class), "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 0);
    mv.visitJumpInsn(Opcodes.GOTO, lTarget);

    // Dead block
    mv.visitLabel(lDead);
    mv.visitFrame(Opcodes.F_FULL, 0, new Object[] {}, 0, new Object[] {});
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);

    // Target label
    mv.visitLabel(lTarget);
    mv.visitFrame(
        Opcodes.F_FULL, 1, new Object[] {binaryName(AuthCheck.class)}, 0, new Object[] {});
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(2, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  static class Main {
    public static RealAuth instance = new RealAuth();

    public static void main(String[] args) {
      AuthCheck a = AuthCheckFactory.make();
      try {
        if (a.isPermitted()) {
          System.out.println("PERMITTED");
        } else {
          System.out.println("DENIED");
        }
      } catch (IncompatibleClassChangeError e) {
        System.out.println("ICCE");
      }
    }
  }

  // Compile stub, the actual code is generated using ASM.
  static class AuthCheckFactory {
    public static AuthCheck make() {
      return null;
    }
  }

  @NoVerticalClassMerging
  public interface AuthCheck {
    boolean isPermitted();
  }

  @NoHorizontalClassMerging
  public static class RealAuth implements AuthCheck {
    @Override
    public boolean isPermitted() {
      return true;
    }
  }

  @NoHorizontalClassMerging
  public static class Fake {
    public boolean isPermitted() {
      return false;
    }
  }
}
