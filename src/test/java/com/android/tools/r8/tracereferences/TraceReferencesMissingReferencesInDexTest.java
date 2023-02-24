// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesMissingReferencesInDexTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static final AndroidApiLevel minApi = AndroidApiLevel.B;

  public TraceReferencesMissingReferencesInDexTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    boolean acceptTypeCalled;
    boolean acceptFieldCalled;
    boolean acceptMethodCalled;

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      acceptTypeCalled = true;
      assertEquals(Reference.classFromClass(Target.class), tracedClass.getReference());
      assertTrue(tracedClass.isMissingDefinition());
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      acceptFieldCalled = true;
      assertEquals(
          Reference.classFromClass(Target.class), tracedField.getReference().getHolderClass());
      assertEquals("field", tracedField.getReference().getFieldName());
      assertTrue(tracedField.isMissingDefinition());
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      acceptMethodCalled = true;
      assertEquals(
          Reference.classFromClass(Target.class), tracedMethod.getReference().getHolderClass());
      assertEquals("target", tracedMethod.getReference().getMethodName());
      assertTrue(tracedMethod.isMissingDefinition());
    }
  }

  private void missingClassReferenced(Path sourceDex) {
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(diagnosticsChecker)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceDex)
              .setConsumer(new TraceReferencesCheckConsumer(consumer))
              .build());
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    assertTrue(consumer.acceptTypeCalled);
    assertTrue(consumer.acceptFieldCalled);
    assertTrue(consumer.acceptMethodCalled);
  }

  @Test
  public void missingClassReferencedInDexArchive() throws Throwable {
    missingClassReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .setMinApi(minApi)
            .compile()
            .writeToZip());
  }

  @Test
  public void missingClassReferencedInDexFile() throws Throwable {
    missingClassReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .setMinApi(minApi)
            .compile()
            .writeToDirectory()
            .resolve("classes.dex"));
  }

  private void missingFieldAndMethodReferenced(Path sourceDex) {
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(diagnosticsChecker)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addSourceFiles(sourceDex)
              .setConsumer(new TraceReferencesCheckConsumer(consumer))
              .build());
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    assertFalse(consumer.acceptTypeCalled);
    assertTrue(consumer.acceptFieldCalled);
    assertTrue(consumer.acceptMethodCalled);
  }

  @Test
  public void missingFieldAndMethodReferencedInDexArchive() throws Throwable {
    missingFieldAndMethodReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .addProgramClassFileData(getClassWithTargetRemoved())
            .setMinApi(minApi)
            .compile()
            .writeToZip());
  }

  @Test
  public void missingFieldAndMethodReferencedInDexFile() throws Throwable {
    missingFieldAndMethodReferenced(
        testForD8(Backend.DEX)
            .addProgramClasses(Source.class)
            .addProgramClassFileData(getClassWithTargetRemoved())
            .setMinApi(minApi)
            .compile()
            .writeToDirectory()
            .resolve("classes.dex"));
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("target"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void target(int i) {}
  }

  static class Source {
    public static void source() {
      Target.target(Target.field);
    }
  }
}
