// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_2_4_20;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// See b/495501447 for context.
@RunWith(Parameterized.class)
public class StepOutOfKotlinFunctionWhereKotlincAddedNopTest extends KotlinDebugTestBase
    implements Opcodes {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // Earlier VMs has different stepping behavior. No need to test that.
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withCfRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .build();
  }

  private static Path kotlinStdlibDex;

  @BeforeClass
  public static void setup() throws Exception {
    kotlinStdlibDex =
        testForD8(getStaticTemp())
            .addProgramFiles(KOTLINC_2_4_20.getCompiler().getKotlinStdlibJar())
            .compile()
            .writeToZip();
  }

  @Test
  public void test() throws Throwable {
    runDebugTest(
        testForRuntime(parameters)
            .addProgramClassFileData(dump())
            .debugConfig(parameters.getRuntime())
            .addPaths(
                parameters.isCfRuntime()
                    ? KOTLINC_2_4_20.getCompiler().getKotlinStdlibJar()
                    : kotlinStdlibDex),
        "B495501447Kt",
        breakpoint("B495501447Kt", "foo"),
        run(),
        checkLine("B495501447.kt", 1),
        stepOut(INTELLIJ_FILTER),
        checkLine("B495501447.kt", parameters.isCfRuntime() ? 4 : 5),
        run());
  }

  /*
   Dump of compiling this Kotlin code with Kotlin 2.4.20:

   fun foo() = 1

   fun main() {
       1.let {
           foo()
       }.toString()
   }
  */
  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "B495501447Kt", null, "java/lang/Object", null);

    classWriter.visitSource("B495501447.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {2, 4, 0});
      annotationVisitor0.visit("k", Integer.valueOf(2));
      annotationVisitor0.visit("xi", Integer.valueOf(48));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u000c\n"
                + "\u0000\n"
                + "\u0002\u0010\u0008\n"
                + "\u0000\n"
                + "\u0002\u0010\u0002\u001a\u0006\u0010\u0000\u001a\u00020\u0001\u001a\u0006\u0010\u0002\u001a\u00020\u0003");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "foo");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "main");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "foo", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "main", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitVarInsn(ISTORE, 0);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "B495501447Kt", "foo", "()I", false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(4, label3);
      methodVisitor.visitInsn(NOP);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(6, label4);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
      methodVisitor.visitInsn(POP);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(7, label5);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitLocalVariable(
          "$i$a$-let-B495501447Kt$main$1", "I", null, label2, label3, 1);
      methodVisitor.visitLocalVariable("it", "I", null, label1, label3, 0);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
              "main",
              "([Ljava/lang/String;)V",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "B495501447Kt", "main", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label1, 0);
      methodVisitor.visitMaxs(0, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
