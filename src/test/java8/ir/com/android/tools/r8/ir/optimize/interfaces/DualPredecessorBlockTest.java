// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.interfaces;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
public class DualPredecessorBlockTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @NoVerticalClassMerging
  public interface I {
    boolean isTrusted();
  }

  @NoHorizontalClassMerging
  public static class Trusted implements I {
    @Override
    public boolean isTrusted() {
      return true;
    }
  }

  @NoHorizontalClassMerging
  public static class B {
    public boolean isTrusted() {
      return true;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        I e = Lib.get(args.length == 999);
        check(e);
      } catch (IncompatibleClassChangeError e) {
        System.out.println("ICCE");
      }
    }

    @NeverInline
    static void check(I e) {
      if (e.isTrusted()) {
        System.out.println("GRANT");
      } else {
        System.out.println("DENY");
      }
    }
  }

  public static class Lib {
    public static I get(boolean b) {
      return null;
    }
  }

  private static byte[] dumpLib() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        V1_6,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$Lib",
        null,
        "java/lang/Object",
        null);

    MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    init.visitCode();
    init.visitVarInsn(ALOAD, 0);
    init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    init.visitInsn(RETURN);
    init.visitMaxs(1, 1);
    init.visitEnd();

    MethodVisitor wt = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "willThrow", "()V", null, null);
    wt.visitCode();
    wt.visitTypeInsn(NEW, "java/lang/RuntimeException");
    wt.visitInsn(DUP);
    wt.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
    wt.visitInsn(ATHROW);
    wt.visitMaxs(2, 0);
    wt.visitEnd();

    MethodVisitor mv =
        cw.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            "get",
            "(Z)Lcom/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$I;",
            null,
            null);
    mv.visitCode();
    Label lNormal = new Label();
    Label lTryStart = new Label();
    Label lTryEnd = new Label();
    Label lMerge = new Label();

    mv.visitTryCatchBlock(lTryStart, lTryEnd, lMerge, "java/lang/Throwable");

    mv.visitVarInsn(ILOAD, 0);
    mv.visitJumpInsn(IFEQ, lNormal);

    // Exceptional path
    mv.visitTypeInsn(
        NEW, "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$Trusted");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(
        INVOKESPECIAL,
        "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$Trusted",
        "<init>",
        "()V",
        false);
    mv.visitVarInsn(ASTORE, 1);

    mv.visitLabel(lTryStart);
    mv.visitMethodInsn(
        INVOKESTATIC,
        "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$Lib",
        "willThrow",
        "()V",
        false);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ARETURN);
    mv.visitLabel(lTryEnd);

    // Normal path
    mv.visitLabel(lNormal);
    mv.visitTypeInsn(NEW, "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$B");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(
        INVOKESPECIAL,
        "com/android/tools/r8/ir/optimize/interfaces/DualPredecessorBlockTest$B",
        "<init>",
        "()V",
        false);
    mv.visitVarInsn(ASTORE, 1);
    mv.visitInsn(ACONST_NULL);
    mv.visitJumpInsn(GOTO, lMerge);

    // Merge point
    mv.visitLabel(lMerge);
    mv.visitInsn(POP);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitInsn(ARETURN);

    mv.visitMaxs(2, 2);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, Trusted.class, B.class, Main.class)
        .addProgramClassFileData(dumpLib())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ICCE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(I.class, Trusted.class, B.class, Main.class)
        .addProgramClassFileData(dumpLib())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class "
                + Lib.class.getName()
                + " { public static "
                + I.class.getName()
                + " get(boolean); }",
            "-neverinline class "
                + Lib.class.getName()
                + " { public static "
                + I.class.getName()
                + " get(boolean); }")
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ICCE");
  }
}
