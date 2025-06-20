// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.sourcefile;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SourceFileAttributeCompatTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  public SourceFileAttributeCompatTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String getOriginalSourceFile() {
    return new Exception().getStackTrace()[0].getFileName();
  }

  private void commonSetUp(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) {
    builder
        .addProgramClasses(TestClass.class, SemiKept.class, NonKept.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep,allowshrinking class " + SemiKept.class.getName() + " { *; }");
  }

  private void checkSourceFileIsRemoved(SingleTestRunResult<?> result) throws Exception {
    if (result.isR8TestRunResult()) {
      // R8 and R8/compat differ from PG in that at least the default source file attribute is
      // retained. This ensures better stack traces on various VMs and has next to no size overhead.
      checkSourceFileIsReplacedByDefault(result);
    } else {
      checkSourceFile(result, null, null, null);
    }
  }

  private void checkSourceFileIsOriginal(SingleTestRunResult<?> result) throws Exception {
    String originalSourceFile = getOriginalSourceFile();
    checkSourceFile(result, originalSourceFile, originalSourceFile, originalSourceFile);
  }

  private void checkSourceFileIsReplacedByDefault(SingleTestRunResult<?> result) throws Exception {
    checkSourceFile(result, "r8-map-id-42", "r8-map-id-42", "r8-map-id-42");
  }

  private void checkSourceFile(
      SingleTestRunResult<?> result, String keptValue, String semiKeptValue, String nonKeptValue)
      throws Exception {
    result.assertFailure();
    result.inspectOriginalStackTrace(
        stackTrace -> {
          StackTraceLine nonKeptLine = stackTrace.get(0);
          StackTraceLine semiKeptLine = stackTrace.get(1);
          StackTraceLine keptLine = stackTrace.get(4);
          assertEquals(getExpectedSourceFile(nonKeptValue), nonKeptLine.fileName);
          assertEquals(getExpectedSourceFile(semiKeptValue), semiKeptLine.fileName);
          assertEquals(getExpectedSourceFile(keptValue), keptLine.fileName);
        });
    result.inspectFailure(
        inspector -> {
          ClassSubject testClass = inspector.clazz(TestClass.class);
          ClassSubject semiKept = inspector.clazz(SemiKept.class);
          ClassSubject nonKept = inspector.clazz(NonKept.class);
          assertEquals(keptValue, getSourceFileString(testClass));
          assertEquals(semiKeptValue, getSourceFileString(semiKept));
          assertEquals(nonKeptValue, getSourceFileString(nonKept));
        });
  }

  private String getSourceFileString(ClassSubject subject) {
    DexString sourceFile = subject.getDexProgramClass().getSourceFile();
    return sourceFile == null ? null : sourceFile.toString();
  }

  private String getExpectedSourceFile(String expectedSourceFileValue) {
    return expectedSourceFileValue == null ? "Unknown Source" : expectedSourceFileValue;
  }

  private <RR extends SingleTestRunResult<RR>> void testJustKeepMain(
      TestShrinkerBuilder<?, ?, ?, RR, ?> builder, boolean fullMode) throws Exception {
    // If the source file attribute is not kept then all compilers will strip it throughout.
    commonSetUp(builder);
    builder.run(parameters.getRuntime(), TestClass.class).apply(this::checkSourceFileIsRemoved);
  }

  private <RR extends SingleTestRunResult<RR>> void testDontObfuscate(
      TestShrinkerBuilder<?, ?, ?, RR, ?> builder, boolean fullMode) throws Exception {
    // If minification is off then compat compilers retain it, full mode will remove it.
    commonSetUp(builder);
    builder
        .addKeepRules("-dontobfuscate")
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(fullMode, this::checkSourceFileIsRemoved, this::checkSourceFileIsOriginal);
  }

  private <RR extends SingleTestRunResult<RR>> void testDontOptimize(
      TestShrinkerBuilder<?, ?, ?, RR, ?> builder, boolean fullMode) throws Exception {
    // No effect from -dontoptimize
    commonSetUp(builder);
    builder
        .addKeepRules("-dontoptimize")
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkSourceFileIsRemoved);
  }

  private <RR extends SingleTestRunResult<RR>> void testDontShrink(
      TestShrinkerBuilder<?, ?, ?, RR, ?> builder, boolean fullMode) throws Exception {
    // No effect from -dontshrink
    commonSetUp(builder);
    builder
        .addKeepRules("-dontshrink")
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkSourceFileIsRemoved);
  }

  private <RR extends SingleTestRunResult<RR>> void testKeepSourceFileAttribute(
      TestShrinkerBuilder<?, ?, ?, RR, ?> builder, boolean fullMode) throws Exception {
    // If the source file attribute is kept, then PG and compat R8 will preserve it in original
    // form for every input class. R8 (full) will replace it by 'SourceFile'. The use of
    // 'SourceFile' is to ensure VMs still print lines.
    commonSetUp(builder);
    builder
        .addKeepAttributeSourceFile()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            fullMode, this::checkSourceFileIsReplacedByDefault, this::checkSourceFileIsOriginal);
  }

  private <RR extends SingleTestRunResult<RR>> void runAllTests(
      Supplier<TestShrinkerBuilder<?, ?, ?, RR, ?>> builder, boolean fullMode) throws Exception {
    testJustKeepMain(builder.get(), fullMode);
    testDontObfuscate(builder.get(), fullMode);
    testDontOptimize(builder.get(), fullMode);
    testDontShrink(builder.get(), fullMode);
    testKeepSourceFileAttribute(builder.get(), fullMode);
  }

  @Test
  public void testR8() throws Exception {
    runAllTests(() -> testForR8(parameters.getBackend()).setMapIdTemplate("42"), true);
  }

  @Test
  public void testCompatR8() throws Exception {
    runAllTests(() -> testForR8Compat(parameters.getBackend()).setMapIdTemplate("42"), false);
  }

  @Test
  public void testPG() throws Exception {
    runAllTests(() -> testForProguard(ProguardVersion.V7_0_0).addDontWarn(getClass()), false);
  }

  static class NonKept {
    @Override
    public String toString() {
      throw new RuntimeException("BOOM!");
    }
  }

  static class SemiKept {
    final Object o;

    public SemiKept(Object o) {
      this.o = o;
    }

    @Override
    public String toString() {
      return o.toString();
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(
          System.nanoTime() > 0
              ? new SemiKept(System.nanoTime() > 0 ? new NonKept() : null)
              : null);
    }
  }
}
