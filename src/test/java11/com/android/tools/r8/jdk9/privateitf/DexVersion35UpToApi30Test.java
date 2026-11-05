// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.privateitf;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DexVersion35UpToApi30Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void testDefaultAndStaticInterfaceMethods() throws Exception {
    TestCompileResult<?, ?> compileResult =
        testForD8(parameters)
            .addProgramClassFileData(getProgramClassFileData(TestClass.class, I.class, A.class))
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true)
            .compile();
    assertDexVersion(
        parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)
            ? DexVersion.V35
            : getDefaultDexVersion(parameters),
        compileResult);
    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutputLines("42");
  }

  @Test
  public void testPrivateInstanceInterfaceMethodInDexInput() throws Exception {
    // DEX input is not desugared, so the private instance interface method remains.
    Path dexInput =
        testForD8(parameters)
            .addProgramClassFileData(
                getProgramClassFileData(
                    TestClassWithPrivate.class, IWithPrivate.class, AWithPrivate.class))
            .compile()
            .writeToZip();
    TestCompilerBuilder<?, ?, ?, ?, ?> builder =
        testForD8(parameters)
            .addProgramFiles(dexInput)
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true);
    if (parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              builder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString("Cannot encode private instance interface method")))));
    } else {
      TestCompileResult<?, ?> compileResult = builder.compile();
      assertDexVersion(getDefaultDexVersion(parameters), compileResult);
      compileResult
          .run(parameters.getRuntime(), TestClassWithPrivate.class)
          .assertSuccessWithOutputLines("42");
    }
  }

  @Test
  public void testNativeInterfaceMethod() throws Exception {
    TestCompilerBuilder<?, ?, ?, ?, ?> builder =
        testForD8(parameters)
            .addProgramClassFileData(createInterfaceWithNativeMethod())
            .addOptionsModification(options -> options.testing.enableDexVersion35UpToApi30 = true);
    if (parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R)) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              builder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString(
                                  "Cannot encode non-static native interface method")))));
    } else {
      TestCompileResult<?, ?> compileResult = builder.compile();
      assertDexVersion(getDefaultDexVersion(parameters), compileResult);
    }
  }

  private List<byte[]> getProgramClassFileData(Class<?>... classes) throws Exception {
    List<byte[]> programClassFileData = new ArrayList<>();
    for (Class<?> clazz : classes) {
      programClassFileData.add(transformer(clazz).clearNest().transform());
    }
    return programClassFileData;
  }

  static DexVersion getDefaultDexVersion(TestParameters parameters) {
    DexVersion dexVersion = DexVersion.getDexVersion(parameters.getApiLevel());
    return dexVersion.isGreaterThan(DexVersion.V39) ? DexVersion.V39 : dexVersion;
  }

  static void assertDexVersion(DexVersion expected, TestCompileResult<?, ?> compileResult)
      throws Exception {
    byte[] dex = ZipUtils.readSingleEntry(compileResult.writeToZip(), "classes.dex");
    assertArrayEquals(expected.getBytes(), Arrays.copyOfRange(dex, 4, 7));
  }

  // Interface with a non-static native method. Such interfaces cannot be written in Java source.
  private static byte[] createInterfaceWithNativeMethod() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
        "NativeInterface",
        null,
        "java/lang/Object",
        null);
    cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "m", "()V", null, null).visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  interface I {
    default int get() {
      return compute();
    }

    static int compute() {
      return 42;
    }
  }

  static class A implements I {}

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(new A().get());
    }
  }

  interface IWithPrivate {
    default int get() {
      return compute();
    }

    private int compute() {
      return 42;
    }
  }

  static class AWithPrivate implements IWithPrivate {}

  static class TestClassWithPrivate {
    public static void main(String[] args) {
      System.out.println(new AWithPrivate().get());
    }
  }
}
