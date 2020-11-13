// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import kotlin.text.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesCommandTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesCommandTest(TestParameters parameters) {}

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    TraceReferencesCommand.builder().build();
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyRun() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No library specified",
        handler -> {
          TraceReferences.run(TraceReferencesCommand.builder(handler).build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyRunCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Missing command",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(new String[] {}, Origin.unknown(), handler).build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void unsupportedCommandCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Missing command, specify one of 'check', '--print-usage' or '--keep-rules'",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(new String[] {"--xxx"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void onlyLibrarySpecified() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No source specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.builder(handler)
                  .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void onlyLibrarySpecifiedCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No source specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {
                        "--check", "--lib", ToolHelper.getAndroidJar(AndroidApiLevel.P).toString()
                      },
                      Origin.unknown(),
                      handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void multipleCommandsSpecified() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Multiple commands specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--check", "--keep-rules"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void allowobfuscationWithoutKeepRule() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Using '--allowobfuscation' requires command '--keep-rules'",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--check", "--allowobfuscation"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void allowobfuscationMultiple() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "No library specified",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--keep-rules", "--allowobfuscation", "--allowobfuscation"},
                      Origin.unknown(),
                      handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void multipleFormatsCommandLine() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Using '--output' requires command '--print-usage' or '--keep-rules'",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--check", "--output", "xxx"}, Origin.unknown(), handler)
                  .build());
        });
  }

  @Test(expected = CompilationFailedException.class)
  public void outputMultiple() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Option '--output' passed multiple times",
        handler -> {
          TraceReferences.run(
              TraceReferencesCommand.parse(
                      new String[] {"--keep-rules", "--output", "xxx", "--output", "xxx"},
                      Origin.unknown(),
                      handler)
                  .build());
        });
  }

  private String formatName(OutputFormat format) {
    if (format == OutputFormat.PRINTUSAGE) {
      return "--print-usage";
    }
    if (format == OutputFormat.KEEP_RULES) {
      return "--keep-rules";
    }
    assertSame(format, OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION);
    return "--keep-rules";
  }

  enum OutputFormat {
    /** Format used with the -printusage flag */
    PRINTUSAGE,
    /** Keep rules keeping each of the traced references */
    KEEP_RULES,
    /**
     * Keep rules with <code>allowobfuscation</code> modifier keeping each of the traced references
     */
    KEEP_RULES_WITH_ALLOWOBFUSCATION
  }

  private static class StringValueStringConsumer implements StringConsumer {
    private StringBuilder builder = new StringBuilder();
    private boolean finished = false;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      builder.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished = true;
    }

    String get() {
      assert finished;
      return builder.toString();
    }
  }

  public void runAndCheckOutput(
      Path targetJar,
      Path sourceJar,
      OutputFormat format,
      String expected,
      Consumer<DiagnosticsChecker> diagnosticsCheckerConsumer)
      throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path output = dir.resolve("output.txt");
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    StringValueStringConsumer stringConsumer = new StringValueStringConsumer();
    TraceReferencesConsumer consumer =
        format == OutputFormat.PRINTUSAGE
            ? new TraceReferencesPrintUsage()
            : TraceReferencesKeepRules.builder()
                .setAllowObfuscation(format == OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION)
                .setOutputConsumer(stringConsumer)
                .build();
    try {
      TraceReferences.run(
          TraceReferencesCommand.builder(diagnosticsChecker)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addTargetFiles(targetJar)
              .addSourceFiles(sourceJar)
              .setConsumer(consumer)
              .build());
      if (consumer instanceof TraceReferencesPrintUsage) {
        assertEquals(expected, ((TraceReferencesPrintUsage) consumer).get());
      } else {
        assertEquals(expected, stringConsumer.get());
      }
      if (diagnosticsCheckerConsumer != null) {
        diagnosticsCheckerConsumer.accept(diagnosticsChecker);
      } else {
        assertEquals(0, diagnosticsChecker.errors.size());
        assertEquals(0, diagnosticsChecker.warnings.size());
        assertEquals(0, diagnosticsChecker.infos.size());
      }
    } catch (CompilationFailedException e) {
      if (diagnosticsCheckerConsumer != null) {
        diagnosticsCheckerConsumer.accept(diagnosticsChecker);
      }
      throw e;
    }

    List<String> args = new ArrayList<>();
    args.add(formatName(format));
    if (format == OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION) {
      args.add("--allowobfuscation");
    }
    args.addAll(
        ImmutableList.of(
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
            "--target",
            targetJar.toString(),
            "--source",
            sourceJar.toString(),
            "--output",
            output.toString()));

    TraceReferences.run(TraceReferencesCommand.parse(args, Origin.unknown()).build());
    assertEquals(expected, FileUtils.readTextFile(output, Charsets.UTF_8));
  }

  public void runAndCheckOutput(
      List<Class<?>> targetClasses,
      List<Class<?>> sourceClasses,
      OutputFormat format,
      String expected)
      throws Throwable {
    runAndCheckOutput(targetClasses, sourceClasses, format, expected, null);
  }

  private Path zipWithTestClasses(Path zipFile, List<Class<?>> targetClasses) throws IOException {
    return ZipBuilder.builder(zipFile)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(),
            targetClasses.stream()
                .map(ToolHelper::getClassFileForTestClass)
                .collect(Collectors.toList()))
        .build();
  }

  public void runAndCheckOutput(
      List<Class<?>> targetClasses,
      List<Class<?>> sourceClasses,
      OutputFormat format,
      String expected,
      Consumer<DiagnosticsChecker> diagnosticsCheckerConsumer)
      throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = zipWithTestClasses(dir.resolve("target.jar"), targetClasses);
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), sourceClasses);
    runAndCheckOutput(targetJar, sourceJar, format, expected, diagnosticsCheckerConsumer);
  }

  @Test
  public void test_printUses() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.PRINTUSAGE,
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: void"
                    + " method(int)",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: int"
                    + " field")));
  }

  @Test
  public void test_keepRules() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.KEEP_RULES,
        StringUtils.lines(
            ImmutableList.of(
                "-keep class com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                    + " {",
                "  public static void method(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  @Test
  public void test_keepRulesAllowObfuscation() throws Throwable {
    runAndCheckOutput(
        ImmutableList.of(Target.class),
        ImmutableList.of(Source.class),
        OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
        StringUtils.lines(
            ImmutableList.of(
                "-keep,allowobfuscation class"
                    + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                "  public static void method(int);",
                "  int field;",
                "}",
                "-keeppackagenames com.android.tools.r8.tracereferences")));
  }

  @Test
  public void test_noOutput() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar = zipWithTestClasses(dir.resolve("target.jar"), ImmutableList.of(Target.class));
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    PrintStream originalOut = System.out;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      System.setOut(new PrintStream(baos));
      TraceReferences.run(
          TraceReferencesCommand.builder()
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .addTargetFiles(targetJar)
              .addSourceFiles(sourceJar)
              .setConsumer(TraceReferencesConsumer.emptyConsumer())
              .build());
      assertEquals(0, baos.size());
    } finally {
      System.setOut(originalOut);
    }

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      System.setOut(new PrintStream(baos));
      TraceReferences.run(
          TraceReferencesCommand.parse(
                  new String[] {
                    "--lib",
                    ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                    "--check",
                    "--target",
                    targetJar.toString(),
                    "--source",
                    sourceJar.toString(),
                  },
                  Origin.unknown())
              .build());
      assertEquals(0, baos.size());
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void test_stdoutOutput() throws Throwable {
    String expected =
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: void"
                    + " method(int)",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: int"
                    + " field"));
    Path dir = temp.newFolder().toPath();
    Path targetJar = zipWithTestClasses(dir.resolve("target.jar"), ImmutableList.of(Target.class));
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    PrintStream originalOut = System.out;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      System.setOut(new PrintStream(baos));
      TraceReferences.run(
          TraceReferencesCommand.parse(
                  new String[] {
                    "--lib",
                    ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                    "--target",
                    targetJar.toString(),
                    "--source",
                    sourceJar.toString(),
                    "--print-usage",
                  },
                  Origin.unknown())
              .build());
      assertEquals(expected, baos.toString(Charsets.UTF_8.name()));
    } finally {
      System.setOut(originalOut);
    }
  }

  public void classFileInput() throws Throwable {
    String expected =
        StringUtils.lines(
            ImmutableList.of(
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: void"
                    + " method(int)",
                "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target: int"
                    + " field"));
    TraceReferencesPrintUsage consumer = new TraceReferencesPrintUsage();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addTargetFiles(ToolHelper.getClassFileForTestClass(Target.class))
            .addSourceFiles(ToolHelper.getClassFileForTestClass(Source.class))
            .setConsumer(consumer)
            .build());
    assertEquals(expected, consumer.get());

    Path output = temp.newFile().toPath();
    TraceReferences.run(
        TraceReferencesCommand.parse(
                new String[] {
                  "--lib", ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                  "--target", ToolHelper.getClassFileForTestClass(Target.class).toString(),
                  "--source", ToolHelper.getClassFileForTestClass(Source.class).toString(),
                  "--output", output.toString(),
                },
                Origin.unknown())
            .build());
    assertEquals(expected, FileUtils.readTextFile(output, Charsets.UTF_8));
  }

  private void checkTargetMissing(DiagnosticsChecker diagnosticsChecker) {
    Field field;
    Method method;
    try {
      field = Target.class.getField("field");
      method = Target.class.getMethod("method", int.class);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    assertEquals(1, diagnosticsChecker.errors.size());
    assertEquals(0, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
    diagnosticsChecker.checkErrorsContains(Reference.classFromClass(Target.class).toString());
    diagnosticsChecker.checkErrorsContains(Reference.fieldFromField(field).toString());
    diagnosticsChecker.checkErrorsContains(Reference.methodFromMethod(method).toString());
  }

  @Test
  public void testNoReferences_printUses() throws Throwable {
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.PRINTUSAGE,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_keepRules() throws Throwable {
    Field field = Target.class.getField("field");
    Method method = Target.class.getMethod("method", int.class);
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.KEEP_RULES,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_keepRulesAllowObfuscation() throws Throwable {
    try {
      runAndCheckOutput(
          ImmutableList.of(OtherTarget.class),
          ImmutableList.of(Source.class),
          OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
          StringUtils.lines(),
          this::checkTargetMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingReference_errorToWarning() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        zipWithTestClasses(dir.resolve("target.jar"), ImmutableList.of(OtherTarget.class));
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    TraceReferences.run(
        TraceReferencesCommand.parse(
                new String[] {
                  "--check",
                  "--lib",
                  ToolHelper.getAndroidJar(AndroidApiLevel.P).toString(),
                  "--target",
                  targetJar.toString(),
                  "--source",
                  sourceJar.toString(),
                  "--map-diagnostics:MissingDefinitionsDiagnostic",
                  "error",
                  "warning"
                },
                Origin.unknown(),
                diagnosticsChecker)
            .build());

    assertEquals(0, diagnosticsChecker.errors.size());
    assertEquals(1, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
  }

  private void checkTargetPartlyMissing(DiagnosticsChecker diagnosticsChecker) {
    Field field;
    Method method;
    try {
      field = Target.class.getField("field");
      method = Target.class.getMethod("method", int.class);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    assertEquals(1, diagnosticsChecker.errors.size());
    assertEquals(0, diagnosticsChecker.warnings.size());
    assertEquals(0, diagnosticsChecker.infos.size());
    diagnosticsChecker.checkErrorsContains(Reference.fieldFromField(field).toString());
    diagnosticsChecker.checkErrorsContains(Reference.methodFromMethod(method).toString());
  }

  @Test
  public void testMissingDefinition_printUses() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved())
            .build();
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.PRINTUSAGE,
          StringUtils.lines(
              ImmutableList.of(
                  "com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingDefinition_keepRules() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved())
            .build();
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.KEEP_RULES,
          StringUtils.lines(
              ImmutableList.of(
                  "-keep class"
                      + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target"
                      + " {",
                  "}",
                  "-keeppackagenames com.android.tools.r8.tracereferences")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testMissingDefinition_keepRulesAllowObfuscation() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(Target.class), getClassWithTargetRemoved())
            .build();
    Path sourceJar = zipWithTestClasses(dir.resolve("source.jar"), ImmutableList.of(Source.class));
    try {
      runAndCheckOutput(
          targetJar,
          sourceJar,
          OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION,
          StringUtils.lines(
              ImmutableList.of(
                  "-keep,allowobfuscation class"
                      + " com.android.tools.r8.tracereferences.TraceReferencesCommandTest$Target {",
                  "}",
                  "-keeppackagenames com.android.tools.r8.tracereferences")),
          this::checkTargetPartlyMissing);
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  private byte[] getClassWithTargetRemoved() throws IOException {
    return transformer(Target.class)
        .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("method"))
        .removeFields((access, name, descriptor, signature, value) -> name.equals("field"))
        .transform();
  }

  static class Target {
    public static int field;

    public static void method(int i) {}
  }

  static class OtherTarget {
    public static void method() {}
  }

  static class Source {
    public static void source() {
      Target.method(Target.field);
    }
  }
}
