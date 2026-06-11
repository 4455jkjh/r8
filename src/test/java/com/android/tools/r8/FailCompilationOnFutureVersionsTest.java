// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class FailCompilationOnFutureVersionsTest extends TestBase {

  static final int UNSUPPORTED_CF_VERSION =
      new InternalOptions().getSupportedCfVersion().major() + 1;
  static final int UNSUPPORTED_DEX_VERSION = InternalOptions.SUPPORTED_DEX_VERSION + 1;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public FailCompilationOnFutureVersionsTest(TestParameters parameters) {
    this.parameters = parameters;
    assertTrue("Test assumes the first DEX version char is '0'.", UNSUPPORTED_DEX_VERSION < 100);
  }

  @Test
  public void testDex() throws CompilationFailedException, IOException {
    // Generate a DEX file with a version higher than the supported one.
    Path out =
        testForD8()
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters)
            .addOptionsModification(
                options ->
                    options.testing.forceDexVersionBytes =
                        ("0" + UNSUPPORTED_DEX_VERSION).getBytes())
            .compile()
            .writeToZip();
    try {
      testForD8()
          .addProgramFiles(out)
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnotics -> {
                diagnotics.assertOnlyErrors();
                diagnotics.assertAllErrorsMatch(
                    diagnosticMessage(
                        containsString(
                            "Unsupported DEX file version: 0" + UNSUPPORTED_DEX_VERSION)));
              });
    } catch (CompilationFailedException e) {
      return;
    }
    fail("Expected compilation error");
  }

  @Test
  public void testCfVersionUnsupportedByAsm() {
    try {
      testForD8()
          .addProgramClassFileData(CfDump.dump(UNSUPPORTED_CF_VERSION))
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnotics -> {
                diagnotics.assertOnlyErrors();
                diagnotics.assertAllErrorsMatch(
                    allOf(
                        diagnosticType(ExceptionDiagnostic.class),
                        diagnosticException(IllegalArgumentException.class),
                        diagnosticMessage(
                            containsString(
                                "Unsupported class file major version "
                                    + UNSUPPORTED_CF_VERSION))));
              });
    } catch (CompilationFailedException e) {
      return;
    }
    fail("Expected compilation error");
  }

  @Test
  public void testCfUnsupportedByD8() {
    try {
      testForD8()
          .addProgramClassFileData(CfDump.dump(CfVersion.V26.major()))
          .setMinApi(parameters)
          .addOptionsModification(
              options -> options.getTestingOptions().supportedCfVersionForTesting = CfVersion.V25)
          .compileWithExpectedDiagnostics(
              diagnotics -> {
                diagnotics.assertOnlyErrors();
                diagnotics.assertAllErrorsMatch(
                    diagnosticMessage(
                        containsString(
                            "Unsupported class file version: " + CfVersion.V26.major())));
              });
    } catch (CompilationFailedException e) {
      return;
    }
    fail("Expected compilation error");
  }

  @Test
  public void testOverrideCfUnsupportedByD8() throws Exception {
    testForD8()
        .addProgramClassFileData(CfDump.dump(CfVersion.V26.major()))
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              options.getTestingOptions().supportedCfVersionForTesting = CfVersion.V25;
              options.getTestingOptions().allowAnyClassFileVersion = true;
            })
        .compile();
  }

  public static class CfDump implements Opcodes {

    public static byte[] dump(int version) {
      // Generate a class file with a version higher than the supported one.
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      MethodVisitor mv;
      cw.visit(version, ACC_PUBLIC + ACC_SUPER, "Test", null, "java/lang/Object", null);
      {
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }

  public static class TestClass {
    // Intentionally empty stub class for the DEX generation.
  }
}
