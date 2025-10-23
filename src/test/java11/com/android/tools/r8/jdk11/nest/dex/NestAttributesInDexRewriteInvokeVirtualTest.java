// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk11.nest.dex;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.PartialCompilationTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NestAttributesInDexRewriteInvokeVirtualTest extends NestAttributesInDexTestBase
    implements Opcodes {

  private static final List<String> EXPECTED_OUTPUT_LINES = ImmutableList.of("Hello, world!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  public NestAttributesInDexRewriteInvokeVirtualTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11)
            && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm(parameters)
        .addProgramClassFileData(dumpHost(), dumpMember1(), dumpMember2())
        .run(parameters.getRuntime(), "Host")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT_LINES);
  }

  private int nonConstructorInvokeDirectCount(MethodSubject method) {
    return (int)
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvokeSpecialOrDirect)
            .filter(instruction -> !instruction.getMethod().getName().toString().equals("<init>"))
            .count();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.canUseNestBasedAccesses());
    testForD8(parameters)
        .addProgramClassFileData(dumpHost(), dumpMember1(), dumpMember2())
        .apply(this::configureEmitNestAnnotationsInDex)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
              assertEquals(
                  2,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "Host")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithClasspathAndMerge() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.canUseNestBasedAccesses());

    Path host =
        testForD8(parameters)
            .addProgramClassFileData(dumpHost())
            .addClasspathClassFileData(dumpMember1(), dumpMember2())
            .apply(this::configureEmitNestAnnotationsInDex)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
                  assertEquals(
                      2,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
                })
            .writeToZip();

    Path member1 =
        testForD8(parameters)
            .addProgramClassFileData(dumpMember1())
            .addClasspathClassFileData(dumpHost(), dumpMember2())
            .apply(this::configureEmitNestAnnotationsInDex)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    Path member2 =
        testForD8(parameters)
            .addProgramClassFileData(dumpMember2())
            .addClasspathClassFileData(dumpHost(), dumpMember1())
            .apply(this::configureEmitNestAnnotationsInDex)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    // Merge using D8.
    testForD8(Backend.DEX, PartialCompilationTestParameters.NONE)
        .addProgramFiles(host, member1, member2)
        .addClasspathClassFileData(dumpMember2())
        .setMinApi(parameters)
        .apply(this::configureEmitNestAnnotationsInDex)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
              assertEquals(
                  2,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "Host")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithoutMembersOnClasspath() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.canUseNestBasedAccesses());
    assertFailsCompilation(
        () ->
            testForD8(parameters)
                .addProgramClassFileData(dumpHost())
                .apply(this::configureEmitNestAnnotationsInDex)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorThatMatches(
                          (diagnosticMessage(containsString("Host requires its nest mates"))));
                    }));
  }

  @Test
  public void testD8WithoutHostOnClasspath() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.canUseNestBasedAccesses());
    assertFailsCompilation(
        () ->
            testForD8(parameters)
                .addProgramClassFileData(dumpMember1(), dumpMember2())
                .apply(this::configureEmitNestAnnotationsInDex)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorThatMatches(
                          (diagnosticMessage(containsString("requires its nest host Host"))));
                    }));
  }

  /*
    Dump of:

    public class Host {
      public static void main(String[] args) {
        new Host().h1();
        System.out.println();
      }

      static class Member1 {
        private void m(Host host) {
          host.h2("Hello");
        }
      }

      static class Member2 {
        private void m(Host host) {
          host.h2(", world!");
        }
      }

      private void h1() {
        new Member1().m(this);
        new Member2().m(this);
      }

      private void h2(String message) {
        System.out.print(message);
      }
    }
  */

  public static byte[] dumpHost() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "Host", null, "java/lang/Object", null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestMember("Host$Member2");
    classWriter.visitNestMember("Host$Member1");
    classWriter.visitInnerClass("Host$Member2", "Host", "Member2", ACC_STATIC);
    classWriter.visitInnerClass("Host$Member1", "Host", "Member1", ACC_STATIC);
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitTypeInsn(NEW, "Host");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host", "<init>", "()V", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host", "h1", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "h1", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(18, label0);
      methodVisitor.visitTypeInsn(NEW, "Host$Member1");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member1", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host$Member1", "m", "(LHost;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(19, label1);
      methodVisitor.visitTypeInsn(NEW, "Host$Member2");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member2", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host$Member2", "m", "(LHost;)V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(20, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "h2", "(Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(23, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(24, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember1() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$Member1", null, "java/lang/Object", null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestHost("Host");
    classWriter.visitInnerClass("Host$Member1", "Host", "Member1", ACC_STATIC);
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(9, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("Hello");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host", "h2", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(10, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember2() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$Member2", null, "java/lang/Object", null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestHost("Host");
    classWriter.visitInnerClass("Host$Member2", "Host", "Member2", ACC_STATIC);
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(13, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(14, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn(", world!");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host", "h2", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
