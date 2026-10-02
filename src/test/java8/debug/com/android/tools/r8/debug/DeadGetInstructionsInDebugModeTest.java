// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.CompilationMode.DEBUG;
import static com.android.tools.r8.CompilationMode.RELEASE;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// See b/496656504 for context.
@RunWith(Parameterized.class)
public class DeadGetInstructionsInDebugModeTest extends DebugTestBase implements Opcodes {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder()
        .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
        .withApiLevel(AndroidApiLevel.B)
        .build();
  }

  @Test
  public void testDeadInstanceGetDebug() throws Throwable {
    testForD8(parameters)
        .addProgramClassFileData(dumpB495480702Kt())
        .addProgramClassFileData(dumpA())
        .setMode(DEBUG)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertTrue(
                  inspector
                      .clazz("B496656504Kt")
                      .uniqueMethodWithOriginalName("foo")
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isStaticGet));
              assertTrue(
                  inspector
                      .clazz("A")
                      .uniqueMethodWithOriginalName("foo")
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isInstanceGet));
            });
  }

  @Test
  public void testDeadGetRelease() throws Throwable {
    testForD8(parameters)
        .addProgramClassFileData(dumpB495480702Kt())
        .addProgramClassFileData(dumpA())
        .setMode(RELEASE)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertTrue(
                  inspector
                      .clazz("B496656504Kt")
                      .uniqueMethodWithOriginalName("foo")
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isStaticGet));
              assertTrue(
                  inspector
                      .clazz("A")
                      .uniqueMethodWithOriginalName("foo")
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isInstanceGet));
            });
  }

  /*
   Dump of compiling this Kotlin code with Kotlin 2.4.20:

   val bar = 1

   fun foo() {
     bar
   }

   class A {
     val bar = 1

     fun foo() {
       bar
     }
   }
  */
  public static byte[] dumpB495480702Kt() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "B496656504Kt", null, "java/lang/Object", null);

    classWriter.visitSource("B496656504.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {2, 4, 0});
      annotationVisitor0.visit("k", Integer.valueOf(2));
      annotationVisitor0.visit("xi", Integer.valueOf(48));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u000e\n"
                + "\u0000\n"
                + "\u0002\u0010\u0008\n"
                + "\u0002\u0008\u0003\n"
                + "\u0002\u0010\u0002\u001a\u0006\u0010\u0004\u001a\u00020\u0005\"\u0014\u0010\u0000\u001a\u00020\u0001X\u0086D\u00a2\u0006\u0008\n"
                + "\u0000\u001a\u0004\u0008\u0002\u0010\u0003");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "bar");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "getBar");
        annotationVisitor1.visit(null, "()I");
        annotationVisitor1.visit(null, "foo");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "bar", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "getBar", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "B496656504Kt", "bar", "I");
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "foo", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "B496656504Kt", "bar", "I");
      methodVisitor.visitInsn(POP);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(5, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitFieldInsn(PUTSTATIC, "B496656504Kt", "bar", "I");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpA() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "A", null, "java/lang/Object", null);

    classWriter.visitSource("B496656504.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {2, 4, 0});
      annotationVisitor0.visit("k", Integer.valueOf(1));
      annotationVisitor0.visit("xi", Integer.valueOf(48));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u0018\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0010\u0000\n"
                + "\u0002\u0008\u0003\n"
                + "\u0002\u0010\u0008\n"
                + "\u0002\u0008\u0003\n"
                + "\u0002\u0010\u0002\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0006\u0010\u0008\u001a\u00020\u0009R\u0014\u0010\u0004\u001a\u00020\u0005X\u0086D\u00a2\u0006\u0008\n"
                + "\u0000\u001a\u0004\u0008\u0006\u0010\u0007");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "LA;");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "<init>");
        annotationVisitor1.visit(null, "()V");
        annotationVisitor1.visit(null, "bar");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "getBar");
        annotationVisitor1.visit(null, "()I");
        annotationVisitor1.visit(null, "foo");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "bar", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(8, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitFieldInsn(PUTFIELD, "A", "bar", "I");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(7, label2);
      methodVisitor.visitInsn(RETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label3, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "getBar", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "A", "bar", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "foo", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(11, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "A", "bar", "I");
      methodVisitor.visitInsn(POP);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(12, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label2, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
