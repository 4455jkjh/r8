// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.naming.InternalMapConsumerImpl;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class L8CommandTest extends CommandTestBase<L8Command> {

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), getJdk8Jdk11());
  }

  public L8CommandTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    parameters.assertNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  protected final Matcher<Diagnostic> cfL8NotSupportedDiagnostic =
      diagnosticMessage(
          containsString("L8 does not support shrinking when generating class files"));

  private StringResource getDesugaredLibraryConfiguration() {
    return StringResource.fromFile(libraryDesugaringSpecification.getSpecification());
  }

  @Override
  public boolean isL8() {
    return true;
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    verifyEmptyCommand(L8Command.builder().build());
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(
        L8Command.builder()
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .build());
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: l8 [options] <input-files>",
            " where <input-files> are any combination class, zip, or jar files",
            " where <input-files> are any combination of dex, class, zip, jar, or apk files",
            " and options are:",
            "  --debug                 # Compile with debugging information (default).",
            "  --release               # Compile without debugging information.",
            "  --output <file>         # Output result in <file>.",
            "                          # <file> must be an existing directory or a zip file.",
            "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
            "  --min-api <number>      # Minimum Android API level compatibility (default: 1).",
            "  --pg-conf <file>        # Proguard configuration <file>.",
            "  --pg-map-output <file>  # Output the resulting name and line mapping to <file>.",
            "  --partition-map-output <file>",
            "                          # Output the resulting mapping to <file>.",
            "  --desugared-lib <file>  # Specify desugared library configuration.",
            "                          # <file> is a desugared library configuration (json).",
            "  --force-enable-assertions[:[<class name>|<package name>...]]",
            "  --force-ea[:[<class name>|<package name>...]]",
            "                          # Forcefully enable javac generated assertion code.",
            "  --force-disable-assertions[:[<class name>|<package name>...]]",
            "  --force-da[:[<class name>|<package name>...]]",
            "                          # Forcefully disable javac generated assertion code.",
            "                          # This is the default handling of javac assertion code",
            "                          # when generating DEX file format.",
            "  --force-passthrough-assertions[:[<class name>|<package name>...]]",
            "  --force-pa[:[<class name>|<package name>...]]",
            "                          # Don't change javac generated assertion code. This",
            "                          # is the default handling of javac assertion code when",
            "                          # generating class file format.",
            "  --force-assertions-handler:<handler method>[:[<class name>|<package name>...]]",
            "  --force-ah:<handler method>[:[<class name>|<package name>...]]",
            "                          # Change javac and kotlinc generated assertion code",
            "                          # to invoke the method <handler method> with each",
            "                          # assertion error instead of throwing it.",
            "                          # The <handler method> is specified as a class name",
            "                          # followed by a dot and the method name.",
            "                          # The handler method must take a single argument of",
            "                          # type java.lang.Throwable and have return type void.",
            "  --thread-count <number> # Use <number> of threads for compilation.",
            "                          # If not specified the number will be based on",
            "                          # heuristics taking the number of cores into account.",
            "  --map-diagnostics[:<type>] <from-level> <to-level>",
            "                          # Map diagnostics of <type> (default any) reported as",
            "                          # <from-level> to <to-level> where <from-level> and",
            "                          # <to-level> are one of 'info', 'warning', or 'error'",
            "                          # and the optional <type> is either the simple or",
            "                          # fully qualified Java type name of a diagnostic.",
            "                          # If <type> is unspecified, all diagnostics at ",
            "                          # <from-level> will be mapped.",
            "                          # Note that fatal compiler errors cannot be mapped.",
            "  --art-profile <input> <output>",
            "                          # Rewrite human readable ART profile read from <input> and"
                + " write to <output>.",
            "  --version               # Print the version of l8.",
            "  --help                  # Print this message."),
        L8CommandParser.getUsageMessage());
  }

  private void verifyEmptyCommand(L8Command command) {
    BaseCompilerCommand compilationCommand =
        command.getD8Command() == null ? command.getR8Command() : command.getD8Command();
    assertNotNull(compilationCommand);
    assertTrue(command.getProgramConsumer() instanceof ClassFileConsumer);
    assertTrue(compilationCommand.getProgramConsumer() instanceof DexIndexedConsumer);
  }

  @Test
  public void testDexMarker() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .addProgramFiles(libraryDesugaringSpecification.getDesugarJdkLibs())
            .setMinApiLevel(20)
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .setOutput(output, OutputMode.DexIndexed)
            .build());
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(output),
        ImmutableList.of(markerTool(Tool.L8), markerTool(Tool.D8)));
  }

  @Test
  public void testClassFileMarker() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .addProgramFiles(libraryDesugaringSpecification.getDesugarJdkLibs())
            .setMinApiLevel(20)
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .setOutput(output, OutputMode.ClassFile)
            .build());
    assertMarkersMatch(ExtractMarkerUtils.extractMarkersFromFile(output), markerTool(Tool.L8));
  }

  private List<String> buildCommand(int minAPI, Path output) {
    ArrayList<String> command = new ArrayList<>();
    for (Path desugarJDKLib : libraryDesugaringSpecification.getDesugarJdkLibs()) {
      command.add(desugarJDKLib.toString());
    }
    for (Path libraryFile : libraryDesugaringSpecification.getLibraryFiles()) {
      command.add("--lib");
      command.add(libraryFile.toString());
    }
    command.add("--min-api");
    command.add(Integer.toString(minAPI));
    command.add("--desugared-lib");
    command.add(libraryDesugaringSpecification.getSpecification().toString());
    command.add("--output");
    command.add(output.toString());
    return command;
  }

  @Test
  public void testDexMarkerCommandLine() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    List<String> command = buildCommand(20, output);
    L8Command l8Command = parse(command.toArray(new String[0]));
    L8.run(l8Command);
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(output),
        ImmutableList.of(markerTool(Tool.L8), markerTool(Tool.D8)));
  }

  @Test
  public void testClassFileMarkerCommandLine() throws Throwable {
    Path output = temp.newFolder().toPath().resolve("desugar_jdk_libs.zip");
    List<String> command = buildCommand(20, output);
    command.add("--classfile");
    L8Command l8Command = parse(command.toArray(new String[0]));
    L8.run(l8Command);
    assertMarkersMatch(ExtractMarkerUtils.extractMarkersFromFile(output), markerTool(Tool.L8));
  }

  @Test
  public void testFlagPgConf() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    Path pgconf = temp.newFolder().toPath().resolve("pg.conf");
    FileUtils.writeTextFile(pgconf, "");
    parse(
        diagnostics,
        "--desugared-lib",
        libraryDesugaringSpecification.getSpecification().toString(),
        "--pg-conf",
        pgconf.toString());
  }

  @Test
  public void testFlagPgConfWithConsumer() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    Path pgconf = temp.newFolder().toPath().resolve("pg.conf");
    Path pgMap = temp.newFolder().toPath().resolve("pg-map.txt");
    FileUtils.writeTextFile(pgconf, "");
    L8Command parsedCommand =
        parse(
            diagnostics,
            "--desugared-lib",
            libraryDesugaringSpecification.getSpecification().toString(),
            "--pg-conf",
            pgconf.toString(),
            "--pg-map-output",
            pgMap.toString());
    assertNotNull(parsedCommand.getR8Command());
    InternalOptions internalOptions = parsedCommand.getR8Command().getInternalOptions();
    assertNotNull(internalOptions);
    assertTrue(internalOptions.mapConsumer instanceof InternalMapConsumerImpl);
    InternalMapConsumerImpl mapStringConsumer =
        (InternalMapConsumerImpl) internalOptions.mapConsumer;
    FileConsumer proguardMapConsumer = (FileConsumer) mapStringConsumer.getMapConsumer();
    assertEquals(pgMap, proguardMapConsumer.getOutputPath());
  }

  @Test
  public void testFlagPgConfWithClassFile() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      Path pgconf = temp.newFolder().toPath().resolve("pg.conf");
      FileUtils.writeTextFile(pgconf, "");
      parse(
          diagnostics,
          "--desugared-lib",
          libraryDesugaringSpecification.getSpecification().toString(),
          "--pg-conf",
          pgconf.toString(),
          "--classfile");
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(cfL8NotSupportedDiagnostic);
    }
  }

  @Test
  public void testFlagPgConfMissingParameter() {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      parse(
          diagnostics,
          "--desugared-lib",
          libraryDesugaringSpecification.getSpecification().toString(),
          "--pg-conf");
      fail("Expected parse error");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(diagnosticMessage(containsString("Missing parameter")));
    }
  }

  private L8Command.Builder prepareBuilder(DiagnosticsHandler handler) {
    return L8Command.builder(handler)
        .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
        .addProgramFiles(libraryDesugaringSpecification.getDesugarJdkLibs())
        .setMinApiLevel(20);
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListNotSupported() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support a main dex list",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .addMainDexListFiles(mainDexList)
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void dexPerClassNotSupported() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support compiling to dex per class",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void desugaredLibrarySpecificationRequired() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 requires a desugared library configuration",
        (handler) ->
            prepareBuilder(handler).setProgramConsumer(ClassFileConsumer.emptyConsumer()).build());
  }

  @Test(expected = CompilationFailedException.class)
  public void proguardMapConsumerNotShrinking() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support defining a map consumer when not shrinking",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setProguardMapConsumer(StringConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void proguardMapOutputNotShrinking() throws Throwable {
    Path pgMapOut = temp.newFile("pg-out.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "L8 does not support defining a map consumer when not shrinking",
        (handler) ->
            prepareBuilder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setProguardMapOutputPath(pgMapOut)
                .build());
  }

  private void addProguardConfigurationString(
      DiagnosticsHandler diagnostics, ProgramConsumer programConsumer) throws Throwable {
    String keepRule = "-keep class java.time.*";
    List<String> keepRules = new ArrayList<>();
    keepRules.add(keepRule);
    L8Command.Builder builder =
        prepareBuilder(diagnostics)
            .setProgramConsumer(programConsumer)
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .addProguardConfiguration(keepRules, Origin.unknown());
    assertTrue(builder.isShrinking());
    assertNotNull(builder.build().getR8Command());
  }

  @Test
  public void addProguardConfigurationStringWithDex() throws Throwable {
    addProguardConfigurationString(
        new TestDiagnosticMessagesImpl(), DexIndexedConsumer.emptyConsumer());
  }

  @Test
  public void addProguardConfigurationStringWithClassFile() throws Throwable {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      addProguardConfigurationString(diagnostics, ClassFileConsumer.emptyConsumer());
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(cfL8NotSupportedDiagnostic);
    }
  }

  private void addProguardConfigurationFile(
      DiagnosticsHandler diagnostics, ProgramConsumer programConsumer) throws Throwable {
    String keepRule = "-keep class java.time.*";
    Path keepRuleFile = temp.newFile("keepRuleFile.txt").toPath();
    Files.write(keepRuleFile, Collections.singletonList(keepRule), StandardCharsets.UTF_8);

    L8Command.Builder builder1 =
        prepareBuilder(diagnostics)
            .setProgramConsumer(programConsumer)
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .addProguardConfigurationFiles(keepRuleFile);
    assertTrue(builder1.isShrinking());
    assertNotNull(builder1.build().getR8Command());

    List<Path> keepRuleFiles = new ArrayList<>();
    keepRuleFiles.add(keepRuleFile);
    L8Command.Builder builder2 =
        prepareBuilder(new TestDiagnosticMessagesImpl())
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addDesugaredLibraryConfiguration(getDesugaredLibraryConfiguration())
            .addProguardConfigurationFiles(keepRuleFiles);
    assertTrue(builder2.isShrinking());
    assertNotNull(builder2.build().getR8Command());
  }

  @Test
  public void addProguardConfigurationFileDex() throws Throwable {
    addProguardConfigurationFile(
        new TestDiagnosticMessagesImpl(), DexIndexedConsumer.emptyConsumer());
  }

  @Test
  public void addProguardConfigurationFileClassFile() throws Throwable {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    try {
      addProguardConfigurationFile(diagnostics, ClassFileConsumer.emptyConsumer());
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      diagnostics.assertErrorsMatch(cfL8NotSupportedDiagnostic);
    }
  }

  @Test
  public void desugaredLibrary() throws CompilationFailedException, IOException {
    ArrayList<String> command = new ArrayList<>();
    command.add("--desugared-lib");
    command.add(libraryDesugaringSpecification.getSpecification().toString());
    for (Path libraryFile : libraryDesugaringSpecification.getLibraryFiles()) {
      command.add("--lib");
      command.add(libraryFile.toString());
    }
    L8Command l8Command = parse(command.toArray(new String[0]));
    InternalOptions options = getOptionsWithLoadedDesugaredLibraryConfiguration(l8Command, true);
    assertFalse(
        options
            .getLibraryDesugaringOptions()
            .getMachineDesugaredLibrarySpecification()
            .getRewriteType()
            .isEmpty());
  }

  private void checkSingleForceAllAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> x) {
    assertEquals(1, entries.size());
    assertTrue(x.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.ALL, entries.get(0).getScope());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> x) {
    assertEquals(2, entries.size());
    assertTrue(x.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertTrue(x.test(entries.get(1)));
    assertEquals(AssertionTransformationScope.PACKAGE, entries.get(1).getScope());
    assertEquals("PackageName", entries.get(1).getValue());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries,
      Predicate<AssertionsConfiguration> checkClass,
      Predicate<AssertionsConfiguration> checkPackage) {
    assertEquals(2, entries.size());
    assertTrue(checkClass.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertTrue(checkPackage.test(entries.get(1)));
    assertEquals(AssertionTransformationScope.PACKAGE, entries.get(1).getScope());
    assertEquals("PackageName", entries.get(1).getValue());
  }

  @Test
  public void forceAssertionOption() throws Exception {
    checkSingleForceAllAssertion(
        parse(
                "--force-enable-assertions",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceAllAssertion(
        parse(
                "--force-disable-assertions",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceAllAssertion(
        parse(
                "--force-passthrough-assertions",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-enable-assertions:ClassName",
                "--force-enable-assertions:PackageName...",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-disable-assertions:ClassName",
                "--force-disable-assertions:PackageName...",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-passthrough-assertions:ClassName",
                "--force-passthrough-assertions:PackageName...",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceAllAssertion(
        parse(
                "--force-assertions-handler:com.example.MyHandler.handler",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"));
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-assertions-handler:com.example.MyHandler.handler1:ClassName",
                "--force-assertions-handler:com.example.MyHandler.handler2:PackageName...",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getAssertionsConfiguration(),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler1")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"),
        configuration ->
            configuration.isAssertionHandler()
                && configuration
                    .getAssertionHandler()
                    .getHolderClass()
                    .equals(Reference.classFromDescriptor("Lcom/example/MyHandler;"))
                && configuration.getAssertionHandler().getMethodName().equals("handler2")
                && configuration
                    .getAssertionHandler()
                    .getMethodDescriptor()
                    .equals("(Ljava/lang/Throwable;)V"));
  }

  @Test
  public void numThreadsOption() throws Exception {
    assertEquals(
        ThreadUtils.NOT_SPECIFIED,
        parse("--desugared-lib", libraryDesugaringSpecification.getSpecification().toString())
            .getThreadCount());
    assertEquals(
        1,
        parse(
                "--thread-count",
                "1",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getThreadCount());
    assertEquals(
        2,
        parse(
                "--thread-count",
                "2",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getThreadCount());
    assertEquals(
        10,
        parse(
                "--thread-count",
                "10",
                "--desugared-lib",
                libraryDesugaringSpecification.getSpecification().toString())
            .getThreadCount());
  }

  private void numThreadsOptionInvalid(String value) {
    final String expectedErrorContains = "Invalid argument to --thread-count";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains,
          handler ->
              parse(
                  handler,
                  "--thread-count",
                  value,
                  "--desugared-lib",
                  libraryDesugaringSpecification.getSpecification().toString()));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void numThreadsOptionInvalid() throws Exception {
    numThreadsOptionInvalid("0");
    numThreadsOptionInvalid("-1");
    numThreadsOptionInvalid("two");
  }

  @Override
  String[] requiredArgsForTest() {
    return new String[] {
      "--desugared-lib", libraryDesugaringSpecification.getSpecification().toString()
    };
  }

  @Override
  L8Command parse(String... args) throws CompilationFailedException {
    return L8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }

  @Override
  L8Command parse(DiagnosticsHandler handler, String... args) throws CompilationFailedException {
    return L8Command.parse(args, EmbeddedOrigin.INSTANCE, handler).build();
  }
}
