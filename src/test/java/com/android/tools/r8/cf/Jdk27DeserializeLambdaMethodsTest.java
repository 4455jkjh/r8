// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class Jdk27DeserializeLambdaMethodsTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("Test");
              assertTrue(clazz.isPresent());
              // TODO(b/521062024): Should be 0.
              assertEquals(
                  2,
                  clazz.allMethods().stream()
                      .filter(
                          method ->
                              method.getOriginalMethodName().startsWith("$deserializeLambda$"))
                      .count());
              // TODO(b/521062024): Should be true.
              assertFalse(
                  clazz.allMethods().stream()
                      .flatMap(MethodSubject::streamInstructions)
                      .filter(InstructionSubject::isInvokeVirtual)
                      .map(instructionSubject -> instructionSubject.getMethod().getName())
                      .noneMatch(name -> name.isEqualTo("getInstantiatedMethodType")));
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .addKeepMainRule("Test")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("Test");
              assertTrue(clazz.isPresent());
              assertEquals(
                  0,
                  clazz.allMethods().stream()
                      .filter(
                          method ->
                              method.getOriginalMethodName().startsWith("$deserializeLambda$"))
                      .count());
              assertTrue(
                  clazz.allMethods().stream()
                      .flatMap(MethodSubject::streamInstructions)
                      .filter(InstructionSubject::isInvokeVirtual)
                      .map(instructionSubject -> instructionSubject.getMethod().getName())
                      .noneMatch(name -> name.isEqualTo("getInstantiatedMethodType")));
            });
  }

  /*

  Byte code for the following Java code compiled with javac HEAD
  (commit 8b2b3c84c59c79362d1a9b8a839edb5999999932) with option --release 27.

  The difference from JDK 26 is that the synthetic method $deserializeLambda$ now has outlines
  for each lambda to deserialize. All the outline method names has prefix $deserializeLambda$.
    class Test {
      private static Supplier<Integer> create0() {
        return (Supplier<Integer> & Serializable) () -> 0;
      }
      private static Supplier<Integer> create1() {
        return (Supplier<Integer> & Serializable) () -> 1;
      }
      private static void runTest(int expectedResult, Supplier<Integer> instance) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
          oos.writeObject(instance);
          oos.close();
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
           ObjectInputStream ois = new ObjectInputStream(in)) {
          int actual = ((Supplier<Integer>) ois.readObject()).get();
          if (expectedResult != actual) {
              throw new AssertionError("Expected: " + expectedResult + ", actual: " + actual);
          }
        }
      }
      public static void main() throws Exception {
        runTest(0, create0());
        runTest(1, create1());
        System.err.println("OK");
      }
    }

  */
  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    // Changed from V27 to V26 as ASM 9.9 does not support V27.
    classWriter.visit(V26, ACC_SUPER, "Test", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    classWriter.visitInnerClass(
        "java/lang/invoke/MethodHandles$Lookup",
        "java/lang/invoke/MethodHandles",
        "Lookup",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC,
              "create0",
              "()Ljava/util/function/Supplier;",
              "()Ljava/util/function/Supplier<Ljava/lang/Integer;>;",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(5, label0);
      methodVisitor.visitInvokeDynamicInsn(
          "get",
          "()Ljava/util/function/Supplier;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "altMetafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "Test",
                "lambda$create0$66102042$1",
                "()Ljava/lang/Integer;",
                false),
            Type.getType("()Ljava/lang/Integer;"),
            Integer.valueOf(5),
            Integer.valueOf(0)
          });
      methodVisitor.visitTypeInsn(CHECKCAST, "java/io/Serializable");
      methodVisitor.visitTypeInsn(CHECKCAST, "java/util/function/Supplier");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC,
              "create1",
              "()Ljava/util/function/Supplier;",
              "()Ljava/util/function/Supplier<Ljava/lang/Integer;>;",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitInvokeDynamicInsn(
          "get",
          "()Ljava/util/function/Supplier;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "altMetafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "Test",
                "lambda$create1$66102042$1",
                "()Ljava/lang/Integer;",
                false),
            Type.getType("()Ljava/lang/Integer;"),
            Integer.valueOf(5),
            Integer.valueOf(0)
          });
      methodVisitor.visitTypeInsn(CHECKCAST, "java/io/Serializable");
      methodVisitor.visitTypeInsn(CHECKCAST, "java/util/function/Supplier");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC,
              "runTest",
              "(ILjava/util/function/Supplier;)V",
              "(ILjava/util/function/Supplier<Ljava/lang/Integer;>;)V",
              new String[] {"java/lang/Exception"});
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
      Label label3 = new Label();
      Label label4 = new Label();
      Label label5 = new Label();
      methodVisitor.visitTryCatchBlock(label3, label4, label5, "java/lang/Throwable");
      Label label6 = new Label();
      Label label7 = new Label();
      Label label8 = new Label();
      methodVisitor.visitTryCatchBlock(label6, label7, label8, "java/lang/Throwable");
      Label label9 = new Label();
      Label label10 = new Label();
      Label label11 = new Label();
      methodVisitor.visitTryCatchBlock(label9, label10, label11, "java/lang/Throwable");
      Label label12 = new Label();
      Label label13 = new Label();
      Label label14 = new Label();
      methodVisitor.visitTryCatchBlock(label12, label13, label14, "java/lang/Throwable");
      Label label15 = new Label();
      Label label16 = new Label();
      Label label17 = new Label();
      methodVisitor.visitTryCatchBlock(label15, label16, label17, "java/lang/Throwable");
      Label label18 = new Label();
      methodVisitor.visitLabel(label18);
      methodVisitor.visitLineNumber(11, label18);
      methodVisitor.visitTypeInsn(NEW, "java/io/ByteArrayOutputStream");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label19 = new Label();
      methodVisitor.visitLabel(label19);
      methodVisitor.visitLineNumber(12, label19);
      methodVisitor.visitTypeInsn(NEW, "java/io/ObjectOutputStream");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "java/io/ObjectOutputStream",
          "<init>",
          "(Ljava/io/OutputStream;)V",
          false);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(13, label0);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/io/ObjectOutputStream",
          "writeObject",
          "(Ljava/lang/Object;)V",
          false);
      Label label20 = new Label();
      methodVisitor.visitLabel(label20);
      methodVisitor.visitLineNumber(14, label20);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectOutputStream", "close", "()V", false);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(15, label1);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectOutputStream", "close", "()V", false);
      Label label21 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label21);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(12, label2);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            Opcodes.INTEGER,
            "java/util/function/Supplier",
            "java/io/ByteArrayOutputStream",
            "java/io/ObjectOutputStream"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectOutputStream", "close", "()V", false);
      methodVisitor.visitLabel(label4);
      Label label22 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label22);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          5,
          new Object[] {
            Opcodes.INTEGER,
            "java/util/function/Supplier",
            "java/io/ByteArrayOutputStream",
            "java/io/ObjectOutputStream",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
      methodVisitor.visitLabel(label22);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label21);
      methodVisitor.visitLineNumber(16, label21);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/io/ByteArrayInputStream");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "toByteArray", "()[B", false);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
      methodVisitor.visitVarInsn(ASTORE, 3);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(17, label12);
      methodVisitor.visitTypeInsn(NEW, "java/io/ObjectInputStream");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/io/ObjectInputStream", "<init>", "(Ljava/io/InputStream;)V", false);
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(18, label6);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectInputStream", "readObject", "()Ljava/lang/Object;", false);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/util/function/Supplier");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
      methodVisitor.visitVarInsn(ISTORE, 5);
      Label label23 = new Label();
      methodVisitor.visitLabel(label23);
      methodVisitor.visitLineNumber(19, label23);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 5);
      methodVisitor.visitJumpInsn(IF_ICMPEQ, label7);
      Label label24 = new Label();
      methodVisitor.visitLabel(label24);
      methodVisitor.visitLineNumber(20, label24);
      methodVisitor.visitTypeInsn(NEW, "java/lang/AssertionError");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 5);
      methodVisitor.visitInvokeDynamicInsn(
          "makeConcatWithConstants",
          "(II)Ljava/lang/String;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/StringConcatFactory",
              "makeConcatWithConstants",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {"Expected: \u0001, actual: \u0001"});
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(22, label7);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND,
          2,
          new Object[] {"java/io/ByteArrayInputStream", "java/io/ObjectInputStream"},
          0,
          null);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectInputStream", "close", "()V", false);
      methodVisitor.visitJumpInsn(GOTO, label13);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(16, label8);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ObjectInputStream", "close", "()V", false);
      methodVisitor.visitLabel(label10);
      Label label25 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label25);
      methodVisitor.visitLabel(label11);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          6,
          new Object[] {
            Opcodes.INTEGER,
            "java/util/function/Supplier",
            "java/io/ByteArrayOutputStream",
            "java/io/ByteArrayInputStream",
            "java/io/ObjectInputStream",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 6);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitVarInsn(ALOAD, 6);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
      methodVisitor.visitLabel(label25);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label13);
      methodVisitor.visitLineNumber(22, label13);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ByteArrayInputStream", "close", "()V", false);
      Label label26 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label26);
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(16, label14);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 4);
      methodVisitor.visitLabel(label15);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/ByteArrayInputStream", "close", "()V", false);
      methodVisitor.visitLabel(label16);
      Label label27 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label27);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          5,
          new Object[] {
            Opcodes.INTEGER,
            "java/util/function/Supplier",
            "java/io/ByteArrayOutputStream",
            "java/io/ByteArrayInputStream",
            "java/lang/Throwable"
          },
          1,
          new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
      methodVisitor.visitLabel(label27);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label26);
      methodVisitor.visitLineNumber(23, label26);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 7);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "()V", null, new String[] {"java/lang/Exception"});
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(25, label0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "Test", "create0", "()Ljava/util/function/Supplier;", false);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "Test", "runTest", "(ILjava/util/function/Supplier;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(26, label1);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "Test", "create1", "()Ljava/util/function/Supplier;", false);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "Test", "runTest", "(ILjava/util/function/Supplier;)V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(27, label2);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("OK");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(28, label3);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$deserializeLambda$",
              "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getImplMethodName",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
      Label label1 = new Label();
      Label label2 = new Label();
      Label label3 = new Label();
      methodVisitor.visitLookupSwitchInsn(
          label3, new int[] {-1258786731, -1129704012}, new Label[] {label1, label2});
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {"java/lang/String", Opcodes.INTEGER}, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("lambda$create0$66102042$1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label3);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("lambda$create1$66102042$1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label3);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 2);
      Label label4 = new Label();
      Label label5 = new Label();
      Label label6 = new Label();
      methodVisitor.visitLookupSwitchInsn(label6, new int[] {0, 1}, new Label[] {label4, label5});
      methodVisitor.visitLabel(label4);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "Test",
          "$deserializeLambda$$lambda$create0$66102042$1",
          "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "Test",
          "$deserializeLambda$$lambda$create1$66102042$1",
          "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("Invalid lambda deserialization");
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "java/lang/IllegalArgumentException",
          "<init>",
          "(Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitMaxs(3, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$deserializeLambda$$lambda$create1$66102042$1",
              "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/invoke/SerializedLambda", "getImplMethodKind", "()I", false);
      methodVisitor.visitIntInsn(BIPUSH, 6);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ICMPNE, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceClass",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("java/util/function/Supplier");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceMethodName",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("get");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceMethodSignature",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Object;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getImplClass",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("Test");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getImplMethodSignature",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Integer;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getInstantiatedMethodType",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Integer;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitInvokeDynamicInsn(
          "get",
          "()Ljava/util/function/Supplier;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "altMetafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "Test",
                "lambda$create1$66102042$1",
                "()Ljava/lang/Integer;",
                false),
            Type.getType("()Ljava/lang/Integer;"),
            Integer.valueOf(5),
            Integer.valueOf(0)
          });
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("Invalid lambda deserialization");
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "java/lang/IllegalArgumentException",
          "<init>",
          "(Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "$deserializeLambda$$lambda$create0$66102042$1",
              "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/invoke/SerializedLambda", "getImplMethodKind", "()I", false);
      methodVisitor.visitIntInsn(BIPUSH, 6);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ICMPNE, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceClass",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("java/util/function/Supplier");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceMethodName",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("get");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getFunctionalInterfaceMethodSignature",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Object;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getImplClass",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("Test");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getImplMethodSignature",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Integer;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/SerializedLambda",
          "getInstantiatedMethodType",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitLdcInsn("()Ljava/lang/Integer;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitInvokeDynamicInsn(
          "get",
          "()Ljava/util/function/Supplier;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "altMetafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "Test",
                "lambda$create0$66102042$1",
                "()Ljava/lang/Integer;",
                false),
            Type.getType("()Ljava/lang/Integer;"),
            Integer.valueOf(5),
            Integer.valueOf(0)
          });
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("Invalid lambda deserialization");
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "java/lang/IllegalArgumentException",
          "<init>",
          "(Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "lambda$create1$66102042$1",
              "()Ljava/lang/Integer;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "lambda$create0$66102042$1",
              "()Ljava/lang/Integer;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(5, label0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
