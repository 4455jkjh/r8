// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b139991218;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// This is the generated bytecode for a kotlin style lambda:
// { it.id }
// defined in com.android.tools.r8.naming.b139991218.Main.java.
// The only added thing is that the String invoke(Alpha) now has a generic type signature.
public class Lambda2 implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/naming/b139991218/Lambda2",
        "Lkotlin/jvm/internal/Lambda;Lkotlin/jvm/functions/Function1<Lcom/android/tools/r8/naming/b139991218/Alpha;Ljava/lang/String;>;",
        "kotlin/jvm/internal/Lambda",
        new String[] {"kotlin/jvm/functions/Function1"});

    classWriter.visitSource("main.kt", null);

    classWriter.visitOuterClass(
        "com/android/tools/r8/naming/b139991218/MainKt", "testMethodAnnotation", "()V");

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 13});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(3));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u000e\n"
                + "\u0000\n"
                + "\u0002\u0010\u000e\n"
                + "\u0000\n"
                + "\u0002\u0018\u0002\n"
                + "\u0000\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\n"
                + "\u00a2\u0006\u0002\u0008\u0004");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "<anonymous>");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "it");
        annotationVisitor1.visit(null, "Lcom/android/tools/r8/naming/b139991218/Alpha;");
        annotationVisitor1.visit(null, "invoke");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitInnerClass(
        "com/android/tools/r8/naming/b139991218/Lambda2", null, null, ACC_FINAL | ACC_STATIC);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
              "INSTANCE",
              "Lcom/android/tools/r8/naming/b139991218/Lambda2;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
              "invoke",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/android/tools/r8/naming/b139991218/Alpha");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/naming/b139991218/Lambda2",
          "invoke",
          "(Lcom/android/tools/r8/naming/b139991218/Alpha;)Ljava/lang/String;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL,
              "invoke",
              "(Lcom/android/tools/r8/naming/b139991218/Alpha;)Ljava/lang/String;",
              "(Lcom/android/tools/r8/naming/b139991218/Alpha;)Ljava/lang/String;",
              null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitAnnotableParameterCount(1, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("it");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(63, label1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/naming/b139991218/Alpha",
          "getId",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitInsn(ARETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/android/tools/r8/naming/b139991218/Lambda2;", null, label0, label2, 0);
      methodVisitor.visitLocalVariable(
          "it", "Lcom/android/tools/r8/naming/b139991218/Alpha;", null, label0, label2, 1);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "kotlin/jvm/internal/Lambda", "<init>", "(I)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/naming/b139991218/Lambda2");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "com/android/tools/r8/naming/b139991218/Lambda2", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/naming/b139991218/Lambda2",
          "INSTANCE",
          "Lcom/android/tools/r8/naming/b139991218/Lambda2;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
