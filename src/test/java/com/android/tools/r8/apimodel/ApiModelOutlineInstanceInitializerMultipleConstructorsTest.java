// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.hamcrest.CoreMatchers;
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
public class ApiModelOutlineInstanceInitializerMultipleConstructorsTest extends TestBase
    implements Opcodes {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.B;
  private static final AndroidApiLevel newConstructorApiLevel = AndroidApiLevel.U;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClassFileData(getTester(), getTransformedMain())
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredConstructor(String.class), newConstructorApiLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getMethod("print"), classApiLevel));
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    try {
      testForD8()
          .setMode(CompilationMode.DEBUG)
          .apply(this::setupTestBuilder)
          .compile()
          .addBootClasspathClasses(LibraryClass.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("Hello World!");
    } catch (CompilationFailedException e) {
      assertTrue(
          parameters.getApiLevel().isBetweenBothIncluded(AndroidApiLevel.L, AndroidApiLevel.T));
      assertEquals(AssertionError.class, e.getCause().getClass());
      assertThat(
          e.getCause().getMessage(),
          CoreMatchers.containsString("Unexpected values live at entry"));
    }
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    try {
      testForD8()
          .setMode(CompilationMode.RELEASE)
          .apply(this::setupTestBuilder)
          .compile()
          .addBootClasspathClasses(LibraryClass.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("Hello World!");
    } catch (CompilationFailedException e) {
      assertTrue(
          parameters.getApiLevel().isBetweenBothIncluded(AndroidApiLevel.L, AndroidApiLevel.T));
      assertEquals(AssertionError.class, e.getCause().getClass());
      assertThat(
          e.getCause().getMessage(),
          CoreMatchers.containsString("Unexpected values live at entry"));
    }
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .apply(this::setupTestBuilder)
          .addKeepMainRule(Main.class)
          .compile()
          .addBootClasspathClasses(LibraryClass.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("Hello World!");
    } catch (CompilationFailedException e) {
      assertTrue(
          parameters.getApiLevel().isBetweenBothIncluded(AndroidApiLevel.L, AndroidApiLevel.T));
      assertEquals(AssertionError.class, e.getCause().getClass());
      assertThat(
          e.getCause().getMessage(),
          CoreMatchers.containsString("Unexpected values live at entry"));
    }
  }

  public static class LibraryClass {
    public LibraryClass() {}

    public LibraryClass(String s) {}

    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class TesterStub {
    public static void test(boolean b) {
      throw new RuntimeException("Stub!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TesterStub.test(AndroidBuildVersion.VERSION >= 34);
    }
  }

  private byte[] getTransformedMain() {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(descriptor(TesterStub.class), "LTester;")
        .transform();
  }

  private byte[] getTester() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "Tester", null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    // Create new LibraryClass using single new instruction conditionally flowing to one for its
    // two constructors.
    //
    // new LibraryClass
    // dup
    // iload 0
    // ifeq elseLabel
    // ldc "Hello"
    // invokespecial LibraryClass.<init>(String)
    // goto endLabel
    // elseLabel:
    // invokespecial LibraryClass.<init>()
    // endLabel:
    // invokevirtual LibraryClass.print()
    // return
    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "(Z)V", null, null);
    mv.visitCode();
    Label newLabel = new Label();
    Label elseLabel = new Label();
    Label endLabel = new Label();
    String libClass = binaryName(LibraryClass.class);
    mv.visitLabel(newLabel);
    mv.visitTypeInsn(NEW, libClass);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ILOAD, 0);
    mv.visitJumpInsn(IFEQ, elseLabel);
    mv.visitLdcInsn("Hello");
    mv.visitMethodInsn(INVOKESPECIAL, libClass, "<init>", "(Ljava/lang/String;)V", false);
    mv.visitJumpInsn(GOTO, endLabel);
    mv.visitLabel(elseLabel);
    mv.visitFrame(F_FULL, 1, new Object[] {INTEGER}, 2, new Object[] {newLabel, newLabel});
    mv.visitMethodInsn(INVOKESPECIAL, libClass, "<init>", "()V", false);
    mv.visitLabel(endLabel);
    mv.visitFrame(F_FULL, 1, new Object[] {INTEGER}, 1, new Object[] {libClass});
    mv.visitMethodInsn(INVOKEVIRTUAL, libClass, "print", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(3, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
