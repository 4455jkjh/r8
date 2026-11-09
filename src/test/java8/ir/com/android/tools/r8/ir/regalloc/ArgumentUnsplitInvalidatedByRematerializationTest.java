// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.base.Strings;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Regression test for a register allocation issue where unsplitting an argument into its input
 * register is invalidated by a later change to the set of unused registers.
 *
 * <p>In 16 bit register allocation mode the allocator inserts moves, computes the unused registers
 * and then rewrites argument splits with an 8 bit constraint to use the argument register when the
 * argument register is below 256. It then inserts moves again and recomputes the unused registers.
 * Between the two rounds, phi moves propagate the max non-spilled register of a phi (located in an
 * argument register through an invoke/range) to the constant flowing into the phi. This makes the
 * constant no longer rematerializable in the second round, so its spill register becomes used, all
 * argument registers shift up by one, and the argument used by `if-eqz` ends up in v256, which is
 * encoded as v0.
 */
@RunWith(Parameterized.class)
public class ArgumentUnsplitInvalidatedByRematerializationTest extends TestBase implements Opcodes {

  private static final String CLASS_NAME = "repro/Test";

  // Shape parameters found by sweeping. The issue only reproduces in a narrow window of register
  // pressure, as the argument register must be exactly at 255 after the first round.
  private static final int OBJECT_ARGUMENTS = 8;
  private static final int OBJECT_LOCALS = 217;
  private static final int PHIS = 8;
  private static final int INT_LOCALS = 20;

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
        .addProgramClassFileData(dump())
        .run(parameters.getRuntime(), "repro.Test")
        .assertSuccessWithOutputLines("hit");
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(christofferqa): Should succeed.
    Assert.assertThrows(
        CompilationFailedException.class,
        () -> testForD8(parameters).addProgramClassFileData(dump()).debug().compile());
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClassFileData(dump())
        .release()
        .run(parameters.getRuntime(), "repro.Test")
        .assertSuccessWithOutputLines("hit");
  }

  private static void pushInt(MethodVisitor mv, int value) {
    if (value >= -1 && value <= 5) {
      mv.visitInsn(ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(BIPUSH, value);
    } else {
      mv.visitIntInsn(SIPUSH, value);
    }
  }

  // Start a new source line. The issue only reproduces with a line number for each statement.
  private static int line(MethodVisitor mv, int line) {
    Label label = new Label();
    mv.visitLabel(label);
    mv.visitLineNumber(line, label);
    return line + 1;
  }

  private static void invoke(MethodVisitor mv, String name, String descriptor) {
    mv.visitMethodInsn(INVOKESTATIC, CLASS_NAME, name, descriptor, false);
  }

  // if (<int on stack> <cmp> <int on stack>) { si(marker); } where the jump skips the body.
  private static void skipUnless(MethodVisitor mv, int jumpOpcode, int marker) {
    Label skip = new Label();
    mv.visitJumpInsn(jumpOpcode, skip);
    pushInt(mv, marker);
    invoke(mv, "si", "(I)V");
    mv.visitLabel(skip);
  }

  public static byte[] dump() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);

    // static boolean c(int i) { return (i & 1) == 0; }
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "c", "(I)Z", null, null);
    mv.visitCode();
    Label falseLabel = new Label();
    mv.visitVarInsn(ILOAD, 0);
    mv.visitInsn(ICONST_1);
    mv.visitInsn(IAND);
    mv.visitJumpInsn(IFNE, falseLabel);
    mv.visitInsn(ICONST_1);
    mv.visitInsn(IRETURN);
    mv.visitLabel(falseLabel);
    mv.visitInsn(ICONST_0);
    mv.visitInsn(IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static void s(Object o) { if (o == null) System.out.println("hit"); }
    mv = cw.visitMethod(ACC_STATIC, "s", "(Ljava/lang/Object;)V", null, null);
    mv.visitCode();
    Label returnLabel = new Label();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitJumpInsn(IFNONNULL, returnLabel);
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitLdcInsn("hit");
    mv.visitMethodInsn(
        INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    mv.visitLabel(returnLabel);
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static void si(int i) {}
    mv = cw.visitMethod(ACC_STATIC, "si", "(I)V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static int f(int i) { return i; }
    mv = cw.visitMethod(ACC_STATIC, "f", "(I)I", null, null);
    mv.visitCode();
    mv.visitVarInsn(ILOAD, 0);
    mv.visitInsn(IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static Object g(int i) { return new Object(); }
    mv = cw.visitMethod(ACC_STATIC, "g", "(I)Ljava/lang/Object;", null, null);
    mv.visitCode();
    mv.visitTypeInsn(NEW, "java/lang/Object");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static void r(int a0, ..., int a7) {}
    String rDescriptor = "(" + Strings.repeat("I", PHIS) + ")V";
    mv = cw.visitMethod(ACC_STATIC, "r", rDescriptor, null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // static void m(int id, Object p1, ..., Object p8)
    String mDescriptor = "(I" + Strings.repeat("Ljava/lang/Object;", OBJECT_ARGUMENTS) + ")V";
    mv = cw.visitMethod(ACC_STATIC, "m", mDescriptor, null, null);
    mv.visitCode();
    int line = 1;
    int nextSlot = 1 + OBJECT_ARGUMENTS;
    // Object l<i> = g(i);
    int firstObjectLocal = nextSlot;
    for (int i = 0; i < OBJECT_LOCALS; i++) {
      line = line(mv, line);
      pushInt(mv, i);
      invoke(mv, "g", "(I)Ljava/lang/Object;");
      mv.visitVarInsn(ASTORE, nextSlot++);
    }
    // int b<q> = f(q + 500);
    int firstIntLocal = nextSlot;
    for (int q = 0; q < INT_LOCALS; q++) {
      line = line(mv, line);
      pushInt(mv, q + 500);
      invoke(mv, "f", "(I)I");
      mv.visitVarInsn(ISTORE, nextSlot++);
    }
    // if (b<q> == b<q+1>) { si(q + 600); }
    for (int q = 0; q < INT_LOCALS; q++) {
      line = line(mv, line);
      mv.visitVarInsn(ILOAD, firstIntLocal + q);
      mv.visitVarInsn(ILOAD, firstIntLocal + (q + 1) % INT_LOCALS);
      skipUnless(mv, IF_ICMPNE, q + 600);
    }
    int firstPhi = nextSlot;
    for (int j = 0; j < PHIS; j++) {
      // int a<j> = c(j + 100) ? 7 : f(j + 100);
      Label elseLabel = new Label();
      Label joinLabel = new Label();
      line = line(mv, line);
      pushInt(mv, j + 100);
      invoke(mv, "c", "(I)Z");
      mv.visitJumpInsn(IFEQ, elseLabel);
      pushInt(mv, 7);
      mv.visitJumpInsn(GOTO, joinLabel);
      mv.visitLabel(elseLabel);
      pushInt(mv, j + 100);
      invoke(mv, "f", "(I)I");
      mv.visitLabel(joinLabel);
      mv.visitVarInsn(ISTORE, nextSlot++);
      // if (a<j> != 7) { si(j + 200); }
      line = line(mv, line);
      mv.visitVarInsn(ILOAD, firstPhi + j);
      pushInt(mv, 7);
      skipUnless(mv, IF_ICMPEQ, j + 200);
      // if (f(j + 300) == 7) { si(j + 400); }
      line = line(mv, line);
      pushInt(mv, j + 300);
      invoke(mv, "f", "(I)I");
      pushInt(mv, 7);
      skipUnless(mv, IF_ICMPNE, j + 400);
    }
    // if (b<q> == 7) { si(q + 700); }
    for (int q = 0; q < INT_LOCALS; q++) {
      line = line(mv, line);
      mv.visitVarInsn(ILOAD, firstIntLocal + q);
      pushInt(mv, 7);
      skipUnless(mv, IF_ICMPNE, q + 700);
    }
    // if (id != 0) { s(null); }
    Label idIsZero = new Label();
    line = line(mv, line);
    mv.visitVarInsn(ILOAD, 0);
    mv.visitJumpInsn(IFEQ, idIsZero);
    mv.visitInsn(ACONST_NULL);
    invoke(mv, "s", "(Ljava/lang/Object;)V");
    mv.visitLabel(idIsZero);
    // if (b<q> == b<q+3>) { si(q + 800); }
    for (int q = 0; q < INT_LOCALS; q++) {
      line = line(mv, line);
      mv.visitVarInsn(ILOAD, firstIntLocal + q);
      mv.visitVarInsn(ILOAD, firstIntLocal + (q + 3) % INT_LOCALS);
      skipUnless(mv, IF_ICMPNE, q + 800);
    }
    // s(p<i>);
    for (int i = 1; i <= OBJECT_ARGUMENTS; i++) {
      line = line(mv, line);
      mv.visitVarInsn(ALOAD, i);
      invoke(mv, "s", "(Ljava/lang/Object;)V");
    }
    // s(l<i>);
    for (int i = 0; i < OBJECT_LOCALS; i++) {
      line = line(mv, line);
      mv.visitVarInsn(ALOAD, firstObjectLocal + i);
      invoke(mv, "s", "(Ljava/lang/Object;)V");
    }
    // r(a0, ..., a7);
    line = line(mv, line);
    for (int j = 0; j < PHIS; j++) {
      mv.visitVarInsn(ILOAD, firstPhi + j);
    }
    invoke(mv, "r", rDescriptor);
    line(mv, line);
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    // public static void main(String[] args) { m(0, ...); m(1, ...); }
    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    for (int id = 0; id <= 1; id++) {
      pushInt(mv, id);
      for (int i = 0; i < OBJECT_ARGUMENTS; i++) {
        mv.visitTypeInsn(NEW, "java/lang/Object");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      }
      invoke(mv, "m", mDescriptor);
    }
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
