// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.R8CommandTest.getOutputPath;
import static com.android.tools.r8.ToolHelper.EXAMPLES_BUILD_DIR;
import static com.android.tools.r8.utils.internal.FileUtils.JAR_EXTENSION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.sdklib.AndroidVersion;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformationScope;
import com.android.tools.r8.D8CommandParser.OrderedClassFileResourceProvider;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class D8CommandTest extends CommandTestBase<D8Command> {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public D8CommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyBuilder() throws Throwable {
    verifyEmptyCommand(D8Command.builder().build());
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(
        D8Command.builder().setProgramConsumer(DexIndexedConsumer.emptyConsumer()).build());
    verifyEmptyCommand(parse());
    verifyEmptyCommand(parse(""));
    verifyEmptyCommand(parse("", ""));
    verifyEmptyCommand(parse(" "));
    verifyEmptyCommand(parse(" ", " "));
    verifyEmptyCommand(parse("\t"));
    verifyEmptyCommand(parse("\t", "\t"));
  }

  private void verifyEmptyCommand(D8Command command) throws Throwable {
    assertEquals(CompilationMode.DEBUG, command.getMode());
    assertEquals(AndroidVersion.DEFAULT.getApiLevel(), command.getMinApiLevel());
    assertTrue(command.getProgramConsumer() instanceof DexIndexedConsumer);
    AndroidApp app = ToolHelper.getApp(command);
    assertEquals(0, app.getDexProgramResourcesForTesting().size());
    assertEquals(0, app.getClassProgramResourcesForTesting().size());
  }

  @Test
  public void allowDexFilePerClassFileBuilder() throws Throwable {
    assertTrue(
        D8Command.builder()
                .setProgramConsumer(DexFilePerClassFileConsumer.emptyConsumer())
                .build()
                .getProgramConsumer()
            instanceof DexFilePerClassFileConsumer);
  }

  @Test
  public void defaultOutIsCwd() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path input = Paths.get(EXAMPLES_BUILD_DIR + "/arithmetic.jar").toAbsolutePath();
    Path output = working.resolve("classes.dex");
    assertFalse(Files.exists(output));
    assertEquals(0, ToolHelper.forkD8(working, input.toString()).exitCode);
    assertTrue(Files.exists(output));
  }

  @Test
  public void flagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flagsFile = working.resolve("flags.txt");
    Path input = Paths.get(EXAMPLES_BUILD_DIR + "/arithmetic.jar").toAbsolutePath();
    Path output = working.resolve("output.zip");
    FileUtils.writeTextFile(
        flagsFile,
        "--output",
        "output.zip",
        "--min-api",
        "24",
        input.toString());
    assertEquals(0, ToolHelper.forkD8(working, "@flags.txt").exitCode);
    assertTrue(Files.exists(output));
    Collection<Marker> markers = ExtractMarkerUtils.extractMarkersFromFile(output);
    assertEquals(1, markers.size());
    Marker marker = markers.iterator().next();
    assertEquals(24, marker.getMinApi().intValue());
    assertEquals(Tool.D8, marker.getTool());
  }

  @Test(expected=CompilationFailedException.class)
  public void nonExistingFlagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flags = working.resolve("flags.txt").toAbsolutePath();
    assertNotEquals(0, ToolHelper.forkR8(working, "@flags.txt").exitCode);
    DiagnosticsChecker.checkErrorsContains(
        "NoSuchFileException",
        handler ->
            D8.run(
                D8Command.parse(
                        new String[] {"@" + flags.toString()}, EmbeddedOrigin.INSTANCE, handler)
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void recursiveFlagsFile() throws Throwable {
    Path working = temp.getRoot().toPath();
    Path flagsFile = working.resolve("flags.txt");
    Path recursiveFlagsFile = working.resolve("recursive_flags.txt");
    Path input = Paths.get(EXAMPLES_BUILD_DIR + "/arithmetic.jar").toAbsolutePath();
    FileUtils.writeTextFile(recursiveFlagsFile, "--output", "output.zip");
    FileUtils.writeTextFile(
        flagsFile, "--min-api", "24", input.toString(), "@" + recursiveFlagsFile);
    DiagnosticsChecker.checkErrorsContains(
        "Recursive @argfiles are not supported",
        handler ->
            D8.run(
                D8Command.parse(
                        new String[] {"@" + flagsFile.toString()}, EmbeddedOrigin.INSTANCE, handler)
                    .build()));
  }

  @Test
  public void printsHelpOnNoInput() throws Throwable {
    ProcessResult result = ToolHelper.forkD8(temp.getRoot().toPath());
    assertFalse(result.exitCode == 0);
    assertTrue(result.stderr.contains("Usage"));
    assertFalse(result.stderr.contains("D8_foobar")); // Sanity check
  }

  @Test
  public void testHelpMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: d8 [options] [@<argfile>] <input-files>",
            " where <input-files> are any combination of dex, class, zip, jar, or apk files",
            " and each <argfile> is a file containing additional arguments (one per line)",
            " and options are:",
            "  --debug                 # Compile with debugging information (default).",
            "  --release               # Compile without debugging information.",
            "  --output <file>         # Output result in <file>.",
            "                          # <file> must be an existing directory or a zip file.",
            "  --globals <file>        # Global synthetics <file> from a previous intermediate"
                + " compilation.",
            "                          # The <file> may be either a zip-archive of global"
                + " synthetics or the",
            "                          # global-synthetic files directly.",
            "  --globals-output <file> # Output global synthetics in <file>.",
            "                          # <file> must be an existing directory or a non-existent zip"
                + " archive.",
            "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
            "  --classpath <file>      # Add <file> as a classpath resource.",
            "  --min-api <number>      # Minimum Android API level compatibility (default: 1).",
            "  --api-database <file>   # Use <file> as the Android API database for API modeling,",
            "                          # overriding the default database.",
            "                          # <file> must be a .ser file generated by"
                + " ApiDatabaseGenerator.",
            "  --pg-map <file>         # Use <file> as a mapping file for distribution.",
            "  --intermediate          # Compile an intermediate result intended for later",
            "                          # merging.",
            "  --file-per-class        # Produce a separate dex file per class.",
            "                          # Synthetic classes are in their own file.",
            "  --file-per-class-file   # Produce a separate dex file per input .class file.",
            "                          # Synthetic classes are with their originating class.",
            "  --no-desugaring         # Force disable desugaring.",
            "  --desugared-lib <file>  # Specify desugared library configuration.",
            "                          # <file> is a desugared library configuration (json).",
            "  --main-dex-rules <file> # Proguard keep rules for classes to place in the",
            "                          # primary dex file.",
            "  --main-dex-list <file>  # List of classes to place in the primary dex file.",
            "  --main-dex-list-output <file>",
            "                          # Output resulting main dex list in <file>.",
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
            "  --android-platform-build",
            "                          # Compile as a platform build where the"
                + " runtime/bootclasspath",
            "                          # is assumed to be the version specified by --min-api.",
            "  --art-profile <input> <output>",
            "                          # Rewrite human readable ART profile read from <input> and"
                + " write to <output>.",
            "  --startup-profile <file>",
            "                          # Startup profile <file> to use for dex layout.",
            "  --verbose-synthetic-names",
            "                          # Enable verbose synthetic names that use the"
                + " `$$ExternalSynthetic` marker.",
            "  --version               # Print the version of d8.",
            "  --help                  # Print this message."),
        D8CommandParser.getUsageMessage());
  }

  @Test
  public void validOutputPath() throws Throwable {
    Path existingDir = temp.getRoot().toPath();
    Path nonExistingZip = existingDir.resolve("a-non-existing-archive.zip");
    assertEquals(
        existingDir,
        getOutputPath(D8Command.builder().setOutput(existingDir, OutputMode.DexIndexed).build()));
    assertEquals(
        nonExistingZip,
        getOutputPath(
            D8Command.builder().setOutput(nonExistingZip, OutputMode.DexIndexed).build()));
    assertEquals(existingDir, getOutputPath(parse("--output", existingDir.toString())));
    assertEquals(nonExistingZip, getOutputPath(parse("--output", nonExistingZip.toString())));
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingOutputDir() throws Throwable {
    Path nonExistingDir = temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    D8Command.builder().setOutput(nonExistingDir, OutputMode.DexIndexed).build();
  }

  @Test
  public void existingOutputDirWithDexFiles() throws Throwable {
    Path existingDir = temp.newFolder().toPath();
    List<Path> classesFiles = ImmutableList.of(
        existingDir.resolve("classes.dex"),
        existingDir.resolve("classes2.dex"),
        existingDir.resolve("Classes3.dex"), // ignore case.
        existingDir.resolve("classes10.dex"),
        existingDir.resolve("classes999.dex"));
    List<Path> otherFiles = ImmutableList.of(
        existingDir.resolve("classes0.dex"),
        existingDir.resolve("classes1.dex"),
        existingDir.resolve("classes010.dex"),
        existingDir.resolve("classesN.dex"),
        existingDir.resolve("other.dex"));
    for (Path file : classesFiles) {
      Files.createFile(file);
      assertTrue(Files.exists(file));
    }
    for (Path file : otherFiles) {
      Files.createFile(file);
      assertTrue(Files.exists(file));
    }
    Path input = Paths.get(EXAMPLES_BUILD_DIR, "arithmetic.jar");
    ProcessResult result =
        ToolHelper.forkD8(Paths.get("."), input.toString(), "--output", existingDir.toString());
    assertEquals(result.toString(), 0, result.exitCode);
    assertTrue(Files.exists(classesFiles.get(0)));
    for (int i = 1; i < classesFiles.size(); i++) {
      Path file = classesFiles.get(i);
      assertFalse("Expected stale file to be gone: " + file, Files.exists(file));
    }
    for (Path file : otherFiles) {
      assertTrue("Expected non-classes file to remain: " + file, Files.exists(file));
    }
  }

  @Test
  public void existingOutputZip() throws Throwable {
    Path existingZip = temp.newFile("an-existing-archive.zip").toPath();
    D8Command.builder().setOutput(existingZip, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileType() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    D8Command.builder().setOutput(invalidType, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingOutputDirParse() throws Throwable {
    Path nonExistingDir = temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    parse("--output", nonExistingDir.toString());
  }

  @Test
  public void existingOutputZipParse() throws Throwable {
    Path existingZip = temp.newFile("an-existing-archive.zip").toPath();
    parse("--output", existingZip.toString());
  }

  @Test
  public void mainDexRules() throws Throwable {
    Path mainDexRules1 = temp.newFile("main-dex-1.rules").toPath();
    Path mainDexRules2 = temp.newFile("main-dex-2.rules").toPath();
    parse("--main-dex-rules", mainDexRules1.toString());
    parse(
        "--main-dex-rules", mainDexRules1.toString(), "--main-dex-rules", mainDexRules2.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingMainDexRules() throws Throwable {
    Path mainDexRules = temp.getRoot().toPath().resolve("main-dex.rules");
    parse("--main-dex-rules", mainDexRules.toString());
  }

  @Test
  public void mainDexList() throws Throwable {
    Path mainDexList1 = temp.newFile("main-dex-list-1.txt").toPath();
    Path mainDexList2 = temp.newFile("main-dex-list-2.txt").toPath();

    D8Command command = parse("--main-dex-list", mainDexList1.toString());
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());

    command = parse(
        "--main-dex-list", mainDexList1.toString(), "--main-dex-list", mainDexList2.toString());
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void nonExistingMainDexList() throws Throwable {
    Path mainDexList = temp.getRoot().toPath().resolve("main-dex-list.txt");
    parse("--main-dex-list", mainDexList.toString());
  }

  @Test
  public void testFlagFilePerClass() throws Throwable {
    D8Command command = parse("--file-per-class");
    assertTrue(command.getProgramConsumer() instanceof DexFilePerClassFileConsumer);
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithFilePerClass() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command = parse("--main-dex-list", mainDexList.toString(), "--file-per-class");
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test
  public void testFlagFilePerClassFile() throws Throwable {
    D8Command command = parse("--file-per-class-file");
    assertTrue(command.getProgramConsumer() instanceof DexFilePerClassFileConsumer);
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithFilePerClassFile() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command = parse("--main-dex-list", mainDexList.toString(), "--file-per-class-file");
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithIntermediate() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command = parse("--main-dex-list", mainDexList.toString(), "--intermediate");
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test
  public void mainDexListNonLegacyMinApiL() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    D8Command command =
        parse(
            "--min-api", Integer.toString(AndroidApiLevel.L.getLevel()),
            "--main-dex-list", mainDexList.toString());
    assertTrue(ToolHelper.getApp(command).hasMainDexListResources());
  }

  @Test(expected = CompilationFailedException.class)
  public void mainDexListWithNonLegacyMinApiAboveL() throws Throwable {
    Path mainDexList = temp.newFile("main-dex-list.txt").toPath();
    DiagnosticsChecker.checkErrorsContains(
        "does not support main-dex",
        (handler) ->
            D8Command.builder(handler)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .setMinApiLevel(AndroidApiLevel.L_MR1.getLevel())
                .addMainDexListFiles(mainDexList)
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidOutputFileTypeParse() throws Throwable {
    Path invalidType = temp.getRoot().toPath().resolve("an-invalid-output-file-type.foobar");
    parse("--output", invalidType.toString());
  }

  @Test
  public void folderLibAndClasspath() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir.toFile());
    D8Command command = parse("--lib", tmpClassesDir.toString(), "--classpath",
        tmpClassesDir.toString());
    AndroidApp inputApp = ToolHelper.getApp(command);
    assertEquals(1, inputApp.getClasspathResourceProviders().size());
    OrderedClassFileResourceProvider classpathProvider =
        (OrderedClassFileResourceProvider) inputApp.getClasspathResourceProviders().get(0);
    assertEquals(1, classpathProvider.providers.size());
    assertTrue(Files.isSameFile(tmpClassesDir,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(0)).getRoot()));
    assertEquals(1, inputApp.getLibraryResourceProviders().size());
    assertTrue(Files.isSameFile(tmpClassesDir,
        ((DirectoryClassFileProvider) inputApp.getLibraryResourceProviders().get(0)).getRoot()));
  }

  @Test
  public void folderClasspathMultiple() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir1 = temp.newFolder().toPath();
    Path tmpClassesDir2 = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir1.toFile());
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir2.toFile());
    D8Command command = parse("--classpath", tmpClassesDir1.toString(), "--classpath",
        tmpClassesDir2.toString());
    AndroidApp inputApp = ToolHelper.getApp(command);
    assertEquals(1, inputApp.getClasspathResourceProviders().size());
    OrderedClassFileResourceProvider classpathProvider =
        (OrderedClassFileResourceProvider) inputApp.getClasspathResourceProviders().get(0);
    assertEquals(2, classpathProvider.providers.size());
    assertTrue(Files.isSameFile(tmpClassesDir1,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(0)).getRoot()));
    assertTrue(Files.isSameFile(tmpClassesDir2,
        ((DirectoryClassFileProvider) classpathProvider.providers.get(1)).getRoot()));
  }

  @Test(expected = CompilationFailedException.class)
  public void classFolderProgram() throws Throwable {
    Path inputFile =
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION);
    Path tmpClassesDir = temp.newFolder().toPath();
    ZipUtils.unzip(inputFile.toString(), tmpClassesDir.toFile());
    parse(tmpClassesDir.toString());
  }

  @Test(expected = CompilationFailedException.class)
  public void emptyFolderProgram() throws Throwable {
    Path tmpClassesDir = temp.newFolder().toPath();
    parse(tmpClassesDir.toString());
  }

  @Test
  public void nonExistingOutputJar() throws Throwable {
    Path nonExistingJar = temp.getRoot().toPath().resolve("non-existing-archive.jar");
    D8Command.builder().setOutput(nonExistingJar, OutputMode.DexIndexed).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void vdexFileUnsupported() throws Throwable {
    Path vdexFile = temp.newFile("test.vdex").toPath();
    D8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .addProgramFiles(vdexFile)
        .build();
  }

  @Test
  public void addProgramResources() throws ResourceException, CompilationFailedException {

    // Stub out a custom origin to identify our resources.
    class MyOrigin extends Origin {

      public MyOrigin() {
        super(Origin.root());
      }

      @Override
      public String part() {
        return "MyOrigin";
      }
    }

    Path input = Paths.get(EXAMPLES_BUILD_DIR, "arithmetic.jar");
    ProgramResourceProvider myProvider =
        ArchiveProgramResourceProvider.fromSupplier(
            new MyOrigin(), () -> new ZipFile(input.toFile(), StandardCharsets.UTF_8));
    D8Command command =
        D8Command.builder()
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addProgramResourceProvider(myProvider)
            .build();

    // Check that each resource was provided by our provider.
    ProgramResourceProvider inAppProvider =
        command.getInputApp().getProgramResourceProviders().get(0);
    for (ProgramResource resource : inAppProvider.getProgramResources()) {
      Origin outermost = resource.getOrigin();
      while (outermost.parent() != null && outermost.parent() != Origin.root()) {
        outermost = outermost.parent();
      }
      assertTrue(outermost instanceof MyOrigin);
    }
  }

  @Test(expected = CompilationFailedException.class)
  public void addMultiTypeProgramConsumer() throws CompilationFailedException {
    class MultiTypeConsumer implements DexIndexedConsumer, DexFilePerClassFileConsumer {

      @Override
      public void accept(
          String primaryClassDescriptor,
          ByteDataView data,
          Set<String> descriptors,
          DiagnosticsHandler handler) {}

      @Override
      public void accept(
          int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {}

      @Override
      public void finished(DiagnosticsHandler handler) {

      }
    }

    D8Command.builder().setProgramConsumer(new MultiTypeConsumer()).build();
  }

  @Test(expected = CompilationFailedException.class)
  public void duplicateApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "multiple --min-api", handler -> parse(handler, "--min-api", "19", "--min-api", "21"));
  }

  @Test(expected = CompilationFailedException.class)
  public void invalidApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "foobar"));
  }

  @Test(expected = CompilationFailedException.class)
  public void negativeApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "-21"));
  }

  @Test(expected = CompilationFailedException.class)
  public void zeroApiLevel() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Invalid argument to --min-api", handler -> parse(handler, "--min-api", "0"));
  }

  @Test
  public void disableDesugaringCli() throws CompilationFailedException {
    BaseCompilerCommandTest.assertDesugaringDisabled(parse("--no-desugaring"));
  }

  @Test
  public void disableDesugaringApi() throws CompilationFailedException {
    BaseCompilerCommandTest.assertDesugaringDisabled(D8Command.builder()
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .setDisableDesugaring(true)
        .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnEmptyClassfile() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.class");
    FileUtils.writeToFile(emptyFile, null, new byte[0]);
    DiagnosticsChecker.checkErrorsContains(
        "empty",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnInvalidClassfileHeader() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.class");
    FileUtils.writeToFile(emptyFile, null, new byte[] {'C', 'A', 'F', 'E', 'B', 'A', 'B', 'F'});
    DiagnosticsChecker.checkErrorsContains(
        "header",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnEmptyDex() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.dex");
    FileUtils.writeToFile(emptyFile, null, new byte[0]);
    DiagnosticsChecker.checkErrorsContains(
        "empty",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test(expected = CompilationFailedException.class)
  public void errorOnInvalidDexHeader() throws IOException, CompilationFailedException {
    Path emptyFile = temp.getRoot().toPath().resolve("empty-file.dex");
    FileUtils.writeToFile(emptyFile, null, new byte[] {'C', 'A', 'F', 'E', 'B', 'A', 'B', 'E'});
    DiagnosticsChecker.checkErrorsContains(
        "header",
        handler ->
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(emptyFile)
                    .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                    .build()));
  }

  @Test
  public void noInputOutputsEmptyZip() throws CompilationFailedException, IOException {
    Path emptyZip = temp.getRoot().toPath().resolve("empty.zip");
    D8.run(
        D8Command.builder()
            .setOutput(emptyZip, OutputMode.DexIndexed)
            .build());
    assertTrue(Files.exists(emptyZip));
    assertEquals(0, new ZipFile(emptyZip.toFile(), StandardCharsets.UTF_8).size());
  }

  private void checkSingleForceAllAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> check) {
    assertEquals(1, entries.size());
    assertTrue(check.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.ALL, entries.get(0).getScope());
  }

  private void checkSingleForceClassAndPackageAssertion(
      List<AssertionsConfiguration> entries, Predicate<AssertionsConfiguration> check) {
    assertEquals(2, entries.size());
    assertTrue(check.test(entries.get(0)));
    assertEquals(AssertionTransformationScope.CLASS, entries.get(0).getScope());
    assertEquals("ClassName", entries.get(0).getValue());
    assertTrue(check.test(entries.get(1)));
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
        parse("--force-enable-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceAllAssertion(
        parse("--force-disable-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceAllAssertion(
        parse("--force-passthrough-assertions").getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceClassAndPackageAssertion(
        parse("--force-enable-assertions:ClassName", "--force-enable-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeEnabled);
    checkSingleForceClassAndPackageAssertion(
        parse("--force-disable-assertions:ClassName", "--force-disable-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isCompileTimeDisabled);
    checkSingleForceClassAndPackageAssertion(
        parse(
                "--force-passthrough-assertions:ClassName",
                "--force-passthrough-assertions:PackageName...")
            .getAssertionsConfiguration(),
        AssertionsConfiguration::isPassthrough);
    checkSingleForceAllAssertion(
        parse("--force-assertions-handler:com.example.MyHandler.handler")
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
                "--force-assertions-handler:com.example.MyHandler.handler2:PackageName...")
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

  @Test(expected = CompilationFailedException.class)
  public void missingParameterForLastOption() throws CompilationFailedException {
    DiagnosticsChecker.checkErrorsContains(
        "Missing parameter", handler -> parse(handler, "--output"));
  }

  @Test
  public void desugaredLibrary() throws CompilationFailedException, IOException {
    D8Command d8Command =
        parse(
            "--desugared-lib",
            LibraryDesugaringSpecification.JDK11.getSpecification().toString(),
            "--lib",
            ToolHelper.getAndroidJar(AndroidApiLevel.R).toString());
    InternalOptions options = getOptionsWithLoadedDesugaredLibraryConfiguration(d8Command, false);
    assertFalse(
        options
            .getLibraryDesugaringOptions()
            .getMachineDesugaredLibrarySpecification()
            .getRewriteType()
            .isEmpty());
  }

  @Test
  public void pgInputMap() throws CompilationFailedException, IOException, ResourceException {
    Path mapFile = temp.newFile().toPath();
    FileUtils.writeTextFile(
        mapFile,
        "com.android.tools.r8.ApiLevelException -> com.android.tools.r8.a:",
        "    boolean $assertionsDisabled -> c");
    D8Command d8Command = parse("--pg-map", mapFile.toString());
    assertFalse(d8Command.getInputApp().getProguardMapInputData().getString().isEmpty());
  }

  @Test
  public void testReoptimize() throws Throwable {
    D8Command.builder()
        .setExperimentalReoptimizeDex(true)
        .setMinApiLevel(24)
        .setMode(CompilationMode.RELEASE)
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .build();
  }

  @Test(expected = CompilationFailedException.class)
  public void testReoptimizeMissingMinApi() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Option --reoptimize-dex requires --min-api",
        handler ->
            D8Command.builder(handler)
                .setExperimentalReoptimizeDex(true)
                .setMode(CompilationMode.RELEASE)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void testReoptimizeWithIntermediate() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Option --reoptimize-dex cannot be used with --intermediate",
        handler ->
            D8Command.builder(handler)
                .setExperimentalReoptimizeDex(true)
                .setMinApiLevel(24)
                .setMode(CompilationMode.RELEASE)
                .setIntermediate(true)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void testReoptimizeWithDebug() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Option --reoptimize-dex requires --release",
        handler ->
            D8Command.builder(handler)
                .setExperimentalReoptimizeDex(true)
                .setMinApiLevel(24)
                .setMode(CompilationMode.DEBUG)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .build());
  }

  @Test(expected = CompilationFailedException.class)
  public void testReoptimizeRequiresRelease() throws Throwable {
    DiagnosticsChecker.checkErrorsContains(
        "Option --reoptimize-dex requires --release",
        handler ->
            D8Command.builder(handler)
                .setExperimentalReoptimizeDex(true)
                .setMinApiLevel(24)
                .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
                .build());
  }

  @Test
  public void numThreadsOption() throws Exception {
    assertEquals(ThreadUtils.NOT_SPECIFIED, parse().getThreadCount());
    assertEquals(1, parse("--thread-count", "1").getThreadCount());
    assertEquals(2, parse("--thread-count", "2").getThreadCount());
    assertEquals(10, parse("--thread-count", "10").getThreadCount());
  }

  private void numThreadsOptionInvalid(String value) {
    final String expectedErrorContains = "Invalid argument to --thread-count";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains, handler -> parse(handler, "--thread-count", value));
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

  @Test
  public void androidPlatformBuildFlag() throws Exception {
    assertFalse(parse().getAndroidPlatformBuild());
    assertTrue(parse("--android-platform-build").getAndroidPlatformBuild());
  }

  @Test
  public void apiDatabaseFlag() throws Exception {
    Path apiDatabase = temp.newFile("api_database.ser").toPath();
    D8Command command = parse("--api-database", apiDatabase.toString());
    assertEquals(apiDatabase, command.getApiDatabasePath());
    assertEquals(apiDatabase, command.getInternalOptions().apiModelingOptions().apiDatabasePath);
  }

  @Test
  public void apiDatabaseDoesNotExistError() throws Exception {
    Path apiDatabase = Paths.get("non_existent_api_database.ser");
    D8Command command = parse("--api-database", apiDatabase.toString());
    InternalOptions options = command.getInternalOptions();
    DiagnosticsChecker handler = new DiagnosticsChecker();
    AndroidApiDataAccess.create(options, handler);
    handler.checkErrorsContains("API database file does not exist: " + apiDatabase);
  }

  @Test
  public void apiDatabaseDuplicateError() throws Exception {
    Path apiDatabase = temp.newFile("api_database.ser").toPath();
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    try {
      parse(
          handler,
          "--api-database",
          apiDatabase.toString(),
          "--api-database",
          apiDatabase.toString());
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      handler
          .assertOnlyErrors()
          .assertErrorsCount(1)
          .assertErrorMessageThatMatches(
              containsString("Cannot set multiple --api-database options"));
    }
  }

  @Override
  String[] requiredArgsForTest() {
    return new String[0];
  }

  @Override
  D8Command parse(String... args) throws CompilationFailedException {
    return D8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }

  @Override
  D8Command parse(DiagnosticsHandler handler, String... args) throws CompilationFailedException {
    return D8Command.parse(args, EmbeddedOrigin.INSTANCE, handler).build();
  }
}
