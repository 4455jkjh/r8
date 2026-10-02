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
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

// See b/495480702 for context.
@RunWith(Parameterized.class)
public class StepOutOfKotlinFunctionTest extends KotlinDebugTestBase implements Opcodes {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // Earlier VMs has different stepping behaviour. No need to test that.
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
        "B495480702Kt",
        breakpoint("B495480702Kt", "foo"),
        run(),
        checkLine("B495480702.kt", 1),
        stepOut(INTELLIJ_FILTER),
        checkLine("B495480702.kt", 4),
        run());
  }

  /*
   Dump of compiling this Kotlin code with Kotlin 2.4.20:

   fun foo(lambda: () -> Int) = lambda()

   fun main() {
       foo { 3 } + 42 // <- Breakpoint here
   }
  */
  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "B495480702Kt", null, "java/lang/Object", null);

    classWriter.visitSource("B495480702.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {2, 4, 0});
      annotationVisitor0.visit("k", Integer.valueOf(2));
      annotationVisitor0.visit("xi", Integer.valueOf(48));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u0012\n"
                + "\u0000\n"
                + "\u0002\u0010\u0008\n"
                + "\u0000\n"
                + "\u0002\u0018\u0002\n"
                + "\u0000\n"
                + "\u0002\u0010\u0002\u001a\u0014\u0010\u0000\u001a\u00020\u00012\u000c\u0010\u0002\u001a\u0008\u0012\u0004\u0012\u00020\u00010\u0003\u001a\u0006\u0010\u0004\u001a\u00020\u0005");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "foo");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "lambda");
        annotationVisitor1.visit(null, "Lkotlin/Function0;");
        annotationVisitor1.visit(null, "main");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
              "foo",
              "(Lkotlin/jvm/functions/Function0;)I",
              "(Lkotlin/jvm/functions/Function0<Ljava/lang/Integer;>;)I",
              null);
      methodVisitor.visitAnnotableParameterCount(1, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("lambda");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkNotNullParameter",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(1, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "kotlin/jvm/functions/Function0",
          "invoke",
          "()Ljava/lang/Object;",
          true);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Number");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false);
      methodVisitor.visitInsn(IRETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "lambda", "Lkotlin/jvm/functions/Function0;", null, label0, label2, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "main", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitInvokeDynamicInsn(
          "invoke",
          "()Lkotlin/jvm/functions/Function0;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "metafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKESTATIC, "B495480702Kt", "main$lambda$0", "()I", false),
            Type.getType("()Ljava/lang/Integer;")
          });
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "B495480702Kt", "foo", "(Lkotlin/jvm/functions/Function0;)I", false);
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(POP);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(5, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
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
      methodVisitor.visitMethodInsn(INVOKESTATIC, "B495480702Kt", "main", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label1, 0);
      methodVisitor.visitMaxs(0, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "main$lambda$0", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
